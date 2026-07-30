package com.elyadata.feature.user.application.mapper;

import com.elyadata.feature.user.application.dto.CreateUserRequest;
import com.elyadata.feature.user.application.dto.UpdateUserRequest;
import com.elyadata.feature.user.application.dto.UserResponse;
import com.elyadata.feature.user.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface UserRestMapper {

    UserResponse toRest(User user);

    List<UserResponse> toRest(List<User> users);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toDomain(CreateUserRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toDomain(UpdateUserRequest request);
}