/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef KONAN_NO_THREADS

#include "ScopedThread.hpp"

#include <pthread.h>
#include <type_traits>

using namespace kotlin;

void internal::setCurrentThreadName(std::string_view name) noexcept {
#if KONAN_MACOSX || KONAN_IOS || KONAN_WATCHOS || KONAN_TVOS
    static_assert(std::is_invocable_r_v<void, decltype(pthread_setname_np), const char*>, "Invalid pthread_setname_np signature");
    pthread_setname_np(name.data());
#else
    static_assert(std::is_invocable_r_v<int, decltype(pthread_setname_np), pthread_t, const char*>, "Invalid pthread_setname_np signature");
    pthread_setname_np(pthread_self(), name.data());
#endif
}

#endif // !KONAN_NO_THREADS
