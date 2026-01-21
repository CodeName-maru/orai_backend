package com.ovengers.chatservice.mongodb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRequestDto {

    @NotBlank(message = "메시지 내용은 필수입니다.")
    @Size(max = 5000, message = "메시지는 5000자를 초과할 수 없습니다.")
    private String content;
}
