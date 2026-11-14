package com.ym.intelligentcollaborativecloudgallery.mapper;

import com.ym.intelligentcollaborativecloudgallery.model.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

/**
* @author yancenxin
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2026-10-28 21:27:00
* @Entity com.ym.intelligentcollaborativecloudgallery.model.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {

    @Select("SELECT * FROM picture WHERE id = #{id} AND isDelete = 0 FOR UPDATE")
    @ResultMap("BaseResultMap")
    Picture selectByIdForUpdate(long id);
}




