package com.hmdp.utils;

/**
 * @author Feg
 * @version 1.0
 */
public interface ILock {

    boolean tryLock(Long timeoutSec);

    void unlock();
}
