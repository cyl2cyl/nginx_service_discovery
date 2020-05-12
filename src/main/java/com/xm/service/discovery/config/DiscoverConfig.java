package com.xm.service.discovery.config;

import lombok.Data;

/**
 * @author caoyilong
 * @description
 * @package com.xm.service.discovery.config
 * @date 2020/05/12 11:51
 */
@Data
public class DiscoverConfig {

    private String upstreamName;
    private String serviceName;

    public DiscoverConfig(String upstreamName, String serviceName) {
        this.upstreamName = upstreamName;
        this.serviceName = serviceName;
    }

    @Override
    public String toString() {
        return "DiscoverConfigBO{" +
                ", upstreamName='" + upstreamName + '\'' +
                ", serviceName='" + serviceName + '\'' +
                '}';
    }

}
