/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "ScopedThread.hpp"

#include <array>
#include <pthread.h>
#include <type_traits>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

#include "Format.h"
#include "KAssert.h"

using namespace kotlin;

namespace {

template <size_t NAME_SIZE = 100>
std::string threadName(pthread_t thread) {
    static_assert(
            std::is_invocable_r_v<int, decltype(pthread_getname_np), pthread_t, char*, size_t>, "Invalid pthread_getname_np signature");
    std::array<char, NAME_SIZE> name;
    int result = pthread_getname_np(thread, name.data(), name.size());
    RuntimeAssert(result == 0, "Failed to get thread name error: %d\n", result);
    // Make sure name is null-terminated.
    name[name.size() - 1] = '\0';
    return std::string(name.data());
}

__attribute__((format(printf, 1, 2))) std::string format(const char* format, ...) {
    std::array<char, 20> buffer;
    std::va_list args;
    va_start(args, format);
    VFormatToSpan(buffer, format, args);
    va_end(args);
    // `buffer` is guaranteed to be 0-terminated.
    return std::string(buffer.data());
}

} // namespace

TEST(ScopedThreadTest, Default) {
    ScopedThread thread([] { EXPECT_THAT(threadName(pthread_self()), ""); });
    EXPECT_THAT(threadName(thread.native_handle()), "");
}

TEST(ScopedThreadTest, ThreadName) {
    ScopedThread thread(ScopedThread::attributes().name("thread name for test"), [] {
        EXPECT_THAT(threadName(pthread_self()), "thread name for test");
    });
    EXPECT_THAT(threadName(thread.native_handle()), "thread name for test");
}

TEST(ScopedThreadTest, DynamicThreadName) {
    ScopedThread thread(
            ScopedThread::attributes().name(format("thread %d", 42)), [] { EXPECT_THAT(threadName(pthread_self()), "thread 42"); });
    EXPECT_THAT(threadName(thread.native_handle()), "thread 42");
}

TEST(ScopedThreadTest, JoinsInDestructor) {
    std::atomic<bool> exited = false;
    {
        ScopedThread([&exited] {
            // Give a chance for the outer scope to go on.
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            exited = true;
        });
    }
    EXPECT_THAT(exited.load(), true);
}

TEST(ScopedThreadTest, ManualJoin) {
    std::atomic<bool> exited = false;
    ScopedThread thread([&exited] {
        // Give a chance for the outer scope to go on.
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
        exited = true;
    });
    EXPECT_THAT(thread.joinable(), true);
    thread.join();
    EXPECT_THAT(thread.joinable(), false);
    EXPECT_THAT(exited.load(), true);
}

TEST(ScopedThreadTest, Detach) {
    std::atomic<bool> exited = false;
    {
        ScopedThread thread([&exited] {
            // Give a chance for the outer scope to go on.
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            exited = true;
        });
        EXPECT_THAT(thread.joinable(), true);
        thread.detach();
        EXPECT_THAT(thread.joinable(), false);
    }
    EXPECT_THAT(exited.load(), false);
    // Wait for the thread to set `exited` before terminating test, otherwise the thread will write
    // into freed `exited`.
    while (!exited.load()) {
    }
}
