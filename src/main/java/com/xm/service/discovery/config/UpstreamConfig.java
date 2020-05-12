package com.xm.service.discovery.pojo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * @author caoyilong
 * @description
 * @package com.xm.service.discovery.pojo
 * @date 2020/05/12 11:51
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "upstream")
public class UpstreamConfig {

    private Set<DiscoverConfigBO> lists;


}
