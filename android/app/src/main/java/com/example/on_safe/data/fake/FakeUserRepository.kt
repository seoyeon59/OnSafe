package com.example.on_safe.data.fake

import com.example.on_safe.data.repository.UserRepository

// TODO: GET /user/profile API 연동 후 교체
class FakeUserRepository : UserRepository {
    override fun getUserName(): String = "보호자"
}