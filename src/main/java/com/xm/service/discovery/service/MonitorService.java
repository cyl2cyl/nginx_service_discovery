package com.xm.service.discovery.service;

import com.alibaba.nacos.api.exception.NacosException;

import java.io.IOException;

public interface MonitorService {

    String PLACEHOLDER = "@{placeholder}";
    String PLACEHOLDER_SERVER = "@{placeholder_server}";
    String UPSTREAM_REG = "upstream\\s*" + PLACEHOLDER + "\\s*\\{[^}]+\\}";
    String UPSTREAM_FOMAT = "upstream " + PLACEHOLDER + " {\n" + PLACEHOLDER_SERVER + "}";

    String NGINX_CMD = "nginx.path";
    String NACOS_ADDR = "nacos_addr";
    String NAMESPACE = "namespace";

    String NGINX_CONFIG = "nginx.config";
    String NGINX_UPSTREAM = "nginx_upstream";
    String NACOS_SERVICE_NAME = "nacos_service_name";
    String RELOAD_INTERVAL = "reload_interval";

    void updateNginxFromNacos() throws IOException, InterruptedException, NacosException;
}
