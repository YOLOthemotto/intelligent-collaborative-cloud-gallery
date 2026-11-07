package com.ym.intelligentcollaborativecloudgallery;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.ym.intelligentcollaborativecloudgallery.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableAsync
public class IntelligentCollaborativeCloudGalleryApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelligentCollaborativeCloudGalleryApplication.class, args);
    }
}
