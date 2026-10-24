package com.ym.intelligentcollaborativecloudgallery;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("com.ym.intelligentcollaborativecloudgallery.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class IntelligentCollaborativeCloudGalleryApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelligentCollaborativeCloudGalleryApplication.class, args);
    }
}
