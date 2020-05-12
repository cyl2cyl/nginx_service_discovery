package com.xm.service.discovery.service.impl;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.xm.service.discovery.config.DiscoverConfig;
import com.xm.service.discovery.config.UpstreamConfig;
import com.xm.service.discovery.service.MonitorService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RefreshScope
public class MonitorServiceImpl implements MonitorService {


    private String PLACEHOLDER = "@{placeholder}";
    private String PLACEHOLDER_SERVER = "@{placeholder_server}";
    private String UPSTREAM_REG = "upstream\\s*" + PLACEHOLDER + "\\s*\\{[^}]+\\}";
    private String UPSTREAM_FOMAT = "upstream " + PLACEHOLDER + " {\n" + PLACEHOLDER_SERVER + "}";

    private String NGINX_CMD = "nginx.path";
    private String NGINX_CONFIG = "nginx.config";
    private String NGINX_UPSTREAM = "nginx_upstream";
    private String NACOS_SERVICE_NAME = "nacos_service_name";

    private static final Logger logger = LoggerFactory.getLogger(MonitorServiceImpl.class);

    private static final String DEFAULT_SERVER = "127.0.0.1:65535";

    private AtomicLong lastReloadTime = new AtomicLong(0);

    @Autowired
    private NacosDiscoveryProperties nacosProperties;


    @Value("${nginx.cmdPath}")
    private String nginxCmd;

    @Value("${nginx.configPath}")
    private String configPath;


    @Value("${nginx.reload.interval:1000}")
    private Integer reloadInterval;

    @Autowired
    private UpstreamConfig upstreamConfig;

    @Override
    public void updateNginxFromNacos() throws IOException, InterruptedException, NacosException {

        //判断nginx的指令是否可用
        if (StringUtils.isEmpty(nginxCmd)) {
            throw new IllegalArgumentException(NGINX_CMD + " is empty");
        }
        int i = -1;
        try {
            Process process = Runtime.getRuntime().exec(nginxCmd + " -V");
            i = process.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new IllegalArgumentException(NGINX_CMD + " is incorrect");
        }
        if (i != 0) {
            throw new IllegalArgumentException(NGINX_CMD + " is incorrect");
        }

        NamingService namingService = nacosProperties.namingServiceInstance();

        //获取配置项
        Set<DiscoverConfig> groupNames = upstreamConfig.getLists();
        if (groupNames.size() == 0) {
            throw new IllegalArgumentException(NGINX_CONFIG + "," + NGINX_UPSTREAM + "," + NACOS_SERVICE_NAME + " are at least one group exists ");
        }

        //开始监听nacos
        for (DiscoverConfig configBO : groupNames) {
            namingService.subscribe(configBO.getServiceName(),
                    event -> {
                        try {
                            List<Instance> instances = namingService.getAllInstances(configBO.getServiceName());
                            //更新nginx中的upstream
                            boolean updated = refreshUpstream(instances, configBO.getUpstreamName(), configPath);
                            if (updated) {
                                lastReloadTime.set(System.currentTimeMillis());
                                logger.info("upstream:{} update success!", configBO.getServiceName());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
            );
        }

        //这边保留原来的处理逻辑
        //开启线程定时reload
        new Thread(() -> {
            Process process = null;
            boolean result = false;
            while (true) {
                if (lastReloadTime.get() == 0L || (System.currentTimeMillis() - lastReloadTime.get()) < reloadInterval) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(reloadInterval);
                        continue;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    //尝试nginx -t ,查看是否有语法错误 0正确 1错误
                    process = Runtime.getRuntime().exec(nginxCmd + " -t");
                    result = process.waitFor(10, TimeUnit.SECONDS);
                    if (!result) {
                        logger.error("nginx timeout , execute [{}] to get detail ", (nginxCmd + " -t"));
                        continue;
                    }
                    if (process.exitValue() != 0) {
                        logger.error("nginx syntax incorrect , execute [{}] to get detail ", (nginxCmd + " -t"));
                        continue;
                    }
                    //nginx reload
                    process = Runtime.getRuntime().exec(nginxCmd + " -s reload");
                    result = process.waitFor(10, TimeUnit.SECONDS);
                    if (!result) {
                        logger.error("nginx timeout , execute [{}] to get detail ", (nginxCmd + " -t"));
                        continue;
                    }
                    if (process.exitValue() != 0) {
                        logger.error("nginx reload incorrect , execute [{}] to get detail ", (nginxCmd + " -s reload"));
                        continue;
                    }
                    lastReloadTime.set(0L);
                    logger.info("nginx reload success!");
                } catch (Exception e) {
                    logger.error("reload nginx throw exception", e);
                }
            }
        }, "reload-nginx").start();

    }

    private boolean refreshUpstream(List<Instance> instances, String nginxUpstream, String nginxConfigPath) {
        //获取到upstream
        Pattern pattern = Pattern.compile(UPSTREAM_REG.replace(PLACEHOLDER, nginxUpstream));
        //判断文件是否存在
        File file = new File(nginxConfigPath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("file : " + nginxConfigPath + " is not exists or not a file");
        }
        Long length = file.length();
        byte[] bytes = new byte[length.intValue()];
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            fileInputStream.read(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //获取到配置文件内容
        String conf = new String(bytes);
        //匹配对应的upstream
        Matcher matcher = pattern.matcher(conf);
        if (matcher.find()) {
            String formatSymbol = "";
            String oldUpstream = matcher.group();
            //计算出旧的upstream到左边的距离
            int index = conf.indexOf(oldUpstream);
            while (index != 0 && (conf.charAt(index - 1) == ' ' || conf.charAt(index - 1) == '\t')) {
                formatSymbol += conf.charAt(index - 1);
                index--;
            }

            //拼接新的upstream
            String newUpstream = UPSTREAM_FOMAT.replace(PLACEHOLDER, nginxUpstream);
            StringBuffer servers = new StringBuffer();
            if (instances.size() > 0) {
                for (Instance instance : instances) {
                    //不健康或不可用的跳过
                    if (!instance.isHealthy() || !instance.isEnabled()) {
                        continue;
                    }
                    String ip = instance.getIp();
                    int port = instance.getPort();
                    servers.append(formatSymbol + "    server " + ip + ":" + port + " max_fails=2 fail_timeout=5s;\n");
                }
            }
            if (servers.length() == 0) {
                //如果没有对应的服务，使用默认的服务防止nginx报错
                servers.append(formatSymbol + "    server " + DEFAULT_SERVER + ";\n");
            }
            servers.append(formatSymbol);
            newUpstream = newUpstream.replace(PLACEHOLDER_SERVER, servers.toString());
            if (oldUpstream.equals(newUpstream)) {
                return false;
            }
            //替换原有的upstream
            conf = matcher.replaceAll(newUpstream);
        } else {
            throw new IllegalArgumentException("can not found upstream:" + nginxUpstream);
        }
        try (FileWriter fileWriter = new FileWriter(file, false)) {
            fileWriter.write(conf);
            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }
}
