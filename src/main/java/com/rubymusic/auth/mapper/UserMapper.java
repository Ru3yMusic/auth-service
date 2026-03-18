package com.rubymusic.auth.mapper;

import com.rubymusic.auth.dto.UserResponse;
import com.rubymusic.auth.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "gender", source = "gender")
    @Mapping(target = "authProvider", source = "authProvider")
    UserResponse toDto(User user);
}
