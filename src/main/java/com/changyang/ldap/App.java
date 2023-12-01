package com.changyang.ldap;

import lombok.extern.slf4j.Slf4j;

/**
 * 主程序
 *
 * @author wangsg
 **/
@Slf4j
public class App {

    public static void main(String[] args) {
        String filepath = args.length == 0 ? "application.properties" : args[0];
        new SyncTask(filepath).run();
    }
}
