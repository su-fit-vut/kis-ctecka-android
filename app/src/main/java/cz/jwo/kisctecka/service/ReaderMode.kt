package cz.jwo.kisctecka.service

enum class ReaderMode {
    SingleRead, ContinuousRead,
    SingleReadAuth, AuthUseKey,
    Idle,
}