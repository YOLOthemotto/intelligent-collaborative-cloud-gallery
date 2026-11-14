package com.ym.intelligentcollaborativecloudgallery.service.impl;

import com.ym.intelligentcollaborativecloudgallery.cache.CacheService;
import com.ym.intelligentcollaborativecloudgallery.exception.BusinessException;
import com.ym.intelligentcollaborativecloudgallery.manager.CosManager;
import com.ym.intelligentcollaborativecloudgallery.manager.upload.FilePictureUpload;
import com.ym.intelligentcollaborativecloudgallery.manager.upload.UrlPictureUpload;
import com.ym.intelligentcollaborativecloudgallery.mapper.PictureMapper;
import com.ym.intelligentcollaborativecloudgallery.mapper.SpaceMapper;
import com.ym.intelligentcollaborativecloudgallery.model.dto.file.UploadPictureResult;
import com.ym.intelligentcollaborativecloudgallery.model.dto.picture.PictureUploadRequest;
import com.ym.intelligentcollaborativecloudgallery.model.entity.Picture;
import com.ym.intelligentcollaborativecloudgallery.model.entity.Space;
import com.ym.intelligentcollaborativecloudgallery.model.entity.User;
import com.ym.intelligentcollaborativecloudgallery.service.SpaceService;
import com.ym.intelligentcollaborativecloudgallery.service.UserService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PictureUploadQuotaTest {
    private final UserService userService = mock(UserService.class);
    private final FilePictureUpload fileUpload = mock(FilePictureUpload.class);
    private final SpaceService spaceService = mock(SpaceService.class);
    private final SpaceMapper spaceMapper = mock(SpaceMapper.class);
    private final PictureMapper pictureMapper = mock(PictureMapper.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private PictureServiceImpl service;
    private User user;
    private Space space;
    private PictureUploadRequest request;

    @BeforeEach
    void setUp() {
        service = spy(new PictureServiceImpl(userService, fileUpload, mock(UrlPictureUpload.class),
                mock(CacheService.class), mock(CosManager.class), spaceService, spaceMapper, transactions));
        ReflectionTestUtils.setField(service, "baseMapper", pictureMapper);
        when(transactions.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        doReturn(true).when(service).saveOrUpdate(any(Picture.class));
        user = new User();
        user.setId(1L);
        space = new Space();
        space.setId(2L);
        space.setTotalCount(1L);
        space.setMaxCount(1L);
        space.setTotalSize(100L);
        space.setMaxSize(100L);
        when(spaceService.getById(2L)).thenReturn(space);
        when(spaceMapper.selectByIdForUpdate(2L)).thenReturn(space);
        when(spaceMapper.updateUsage(anyLong(), anyLong(), anyLong())).thenReturn(1);
        request = new PictureUploadRequest();
        request.setSpaceId(2L);
    }

    private void uploadedSize(long size) {
        UploadPictureResult result = new UploadPictureResult();
        result.setPicName("image");
        result.setUrl("https://example.test/image");
        result.setPicSize(size);
        when(fileUpload.uploadPicture(any(), anyString())).thenReturn(result);
    }

    private Picture existingPicture(long size) {
        Picture picture = new Picture();
        picture.setId(3L);
        picture.setUserId(1L);
        picture.setSpaceId(2L);
        picture.setPicSize(size);
        request.setId(3L);
        doReturn(picture).when(service).getById(3L);
        when(pictureMapper.selectByIdForUpdate(3L)).thenReturn(picture);
        return picture;
    }

    @Test
    void creationAddsCountAndActualSizeAtLimit() {
        space.setTotalCount(0L);
        space.setTotalSize(30L);
        uploadedSize(70);
        service.uploadPicture(new Object(), request, user);
        verify(spaceMapper).updateUsage(2L, 1L, 100L);
    }

    @Test
    void creationRejectsCountAndSizeOverflow() {
        uploadedSize(1);
        assertEquals("空间条数不足", assertThrows(BusinessException.class,
                () -> service.uploadPicture(new Object(), request, user)).getMessage());
        space.setTotalCount(0L);
        assertEquals("空间容量不足", assertThrows(BusinessException.class,
                () -> service.uploadPicture(new Object(), request, user)).getMessage());
        verify(spaceMapper, never()).updateUsage(anyLong(), anyLong(), anyLong());
        verify(service, never()).saveOrUpdate(any(Picture.class));
    }

    @Test
    void replacementAtFullQuotaKeepsCountAndUsesSizeDifference() {
        existingPicture(100);
        uploadedSize(60);
        request.setSpaceId(null);
        service.uploadPicture(new Object(), request, user);
        verify(spaceService).checkSpaceAuth(user, space);
        verify(spaceMapper).updateUsage(2L, 1L, 60L);
    }

    @Test
    void replacementRejectsGrowthPastLimitButAllowsExactLimit() {
        existingPicture(40);
        space.setTotalSize(80L);
        uploadedSize(61);
        assertEquals("空间容量不足", assertThrows(BusinessException.class,
                () -> service.uploadPicture(new Object(), request, user)).getMessage());
        uploadedSize(60);
        service.uploadPicture(new Object(), request, user);
        verify(spaceMapper).updateUsage(2L, 1L, 100L);
    }

    @Test
    void replacementUsesLockedCurrentSizeAndRejectsDeletedPicture() {
        existingPicture(40);
        Picture latest = existingPicture(70);
        doReturn(existingPicture(40)).when(service).getById(3L);
        when(pictureMapper.selectByIdForUpdate(3L)).thenReturn(latest, null);
        uploadedSize(80);
        space.setTotalSize(90L);
        service.uploadPicture(new Object(), request, user);
        verify(spaceMapper).updateUsage(2L, 1L, 100L);
        assertEquals("图片不存在", assertThrows(BusinessException.class,
                () -> service.uploadPicture(new Object(), request, user)).getMessage());
    }

    @Test
    void replacementRejectsDifferentSpace() {
        existingPicture(40);
        request.setSpaceId(4L);
        assertEquals("空间不一致", assertThrows(BusinessException.class,
                () -> service.uploadPicture(new Object(), request, user)).getMessage());
        verifyNoInteractions(fileUpload);
    }

    @Test
    void replacementOfEqualSizeNeedsNoUsageWrite() {
        existingPicture(100);
        uploadedSize(100);
        service.uploadPicture(new Object(), request, user);
        verify(spaceMapper, never()).updateUsage(anyLong(), anyLong(), anyLong());
    }

    @Test
    void deletionUsesLockedPictureSizeAndPublicDeletionSkipsQuota() {
        Picture picture = existingPicture(40);
        doNothing().when(service).checkPictureAuth(any(), any());
        doNothing().when(service).clearPictureFile(any());
        doReturn(true).when(service).removeById(3L);
        service.deletePicture(3L, user);
        verify(spaceMapper).updateUsage(2L, 0L, 60L);
        picture.setSpaceId(null);
        service.deletePicture(3L, user);
        verify(spaceMapper, times(1)).selectByIdForUpdate(2L);
    }

    @Test
    void creationCannotExceedAnAlreadyOverfullSpaceEvenWithZeroSize() {
        space.setTotalCount(0L);
        space.setTotalSize(101L);
        uploadedSize(0);
        assertEquals("空间容量不足", assertThrows(BusinessException.class,
                () -> service.uploadPicture(new Object(), request, user)).getMessage());
    }

    @Test
    void lockingQueriesResolveTheirXmlResultMaps() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (String name : new String[] {"PictureMapper", "SpaceMapper"}) {
            String resource = "mapper/" + name + ".xml";
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
            String statement = "com.ym.intelligentcollaborativecloudgallery.mapper." + name + ".selectByIdForUpdate";
            assertEquals("com.ym.intelligentcollaborativecloudgallery.mapper." + name + ".BaseResultMap",
                    configuration.getMappedStatement(statement).getResultMaps().get(0).getId());
        }
    }
}
