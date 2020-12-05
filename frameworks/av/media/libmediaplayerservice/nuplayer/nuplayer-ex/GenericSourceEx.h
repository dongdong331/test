#ifndef GENERIC_SOURCE_EX_H
#define GENERIC_SOURCE_EX_H
    int mSeekingCount;
    mutable Mutex mSeekingLock;
    void notifySeekDone(status_t err);
    bool isSeeking();
#endif