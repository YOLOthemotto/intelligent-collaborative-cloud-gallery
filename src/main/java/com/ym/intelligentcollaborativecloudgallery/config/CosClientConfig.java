package com.ym.intelligentcollaborativecloudgallery.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cos")
@Data
public class CosClientConfig {
    /**
     * 域名
     */
    private String host;

    /**
     * 腾讯云 API 密钥
     */
    private String secretId;

    /**
     * 腾讯云 API 密钥
     */
    private String secretKey;
    /**
     * 区域
     */
    private String region;
    /**
     * 存储桶名称
     */
    private String bucketName;

    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient() {
        // 1 初始化用户身份信息（secretId, secretKey）。
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        Region region = new Region(this.region);
        ClientConfig clientConfig = new ClientConfig(region);
        clientConfig.setHttpProtocol(HttpProtocol.https);
        return new COSClient(cred, clientConfig);
    }
}
