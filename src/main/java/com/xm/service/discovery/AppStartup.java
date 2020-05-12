package com.xm.service.discovery;

import com.xm.service.discovery.service.MonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @author caoyilong
 * @description
 * @package com.xm.service.discovery
 * @date 2020/05/11 20:48
 */
@Component
public class AppStartup {
    private final static Logger LOGGER = LoggerFactory.getLogger(AppStartup.class);

    @Autowired
    private MonitorService monitorService;

    @PostConstruct
    public void init() {
        try {
            monitorService.updateNginxFromNacos();
        } catch (Exception e) {

        }
    }
}
