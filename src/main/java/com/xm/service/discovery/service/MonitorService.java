package com.xm.service.discovery.service;

import com.alibaba.nacos.api.exception.NacosException;

import java.io.IOException;

public interface MonitorService {

    void updateNginxFromNacos() throws IOException, InterruptedException, NacosException;
}
