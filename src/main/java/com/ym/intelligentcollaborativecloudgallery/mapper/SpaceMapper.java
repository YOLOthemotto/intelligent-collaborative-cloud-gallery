package com.ym.intelligentcollaborativecloudgallery.mapper;

import com.ym.intelligentcollaborativecloudgallery.model.entity.Space;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
* @author yancenxin
* @description 针对表【space(空间)】的数据库操作Mapper
* @createDate 2026-11-07 13:52:46
* @Entity com.ym.intelligentcollaborativecloudgallery.model.entity.Space
*/
public interface SpaceMapper extends BaseMapper<Space> {

    @Select("SELECT * FROM space WHERE id = #{id} AND isDelete = 0 FOR UPDATE")
    @ResultMap("BaseResultMap")
    Space selectByIdForUpdate(long id);

    @Update("UPDATE space SET totalCount = #{count}, totalSize = #{size} WHERE id = #{id} AND isDelete = 0")
    int updateUsage(@Param("id") long id, @Param("count") long count, @Param("size") long size);
}




