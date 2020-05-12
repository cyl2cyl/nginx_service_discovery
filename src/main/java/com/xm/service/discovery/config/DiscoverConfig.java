package com.xm.service.discovery.pojo;

import lombok.Data;

/**
 * @author caoyilong
 * @description
 * @package com.xm.service.discovery.pojo
 * @date 2020/05/12 11:51
 */
@Data
public class DiscoverConfigBO {

    private String upstreamName;
    private String serviceName;

    public DiscoverConfigBO(String upstreamName, String serviceName) {
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
