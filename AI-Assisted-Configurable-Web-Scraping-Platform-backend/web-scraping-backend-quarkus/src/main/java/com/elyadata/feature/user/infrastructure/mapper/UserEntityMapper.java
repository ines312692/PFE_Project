package com.elyadata.feature.user.infrastructure.mapper;

import com.elyadata.feature.user.domain.model.User;
import com.elyadata.feature.user.infrastructure.entity.UserEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface UserEntityMapper {

    User toDomain(UserEntity entity);

    UserEntity toEntity(User user);
}