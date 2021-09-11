# geektime_homework06
## 1. 基本信息

学号: G20210735010190

## 2. 命令及运行截图
### 作业一
#### 步骤
1. 克隆源码
`git clone -b v3.1.2 https://github.com/apache/spark`
2. 使用idea打开
3. 下载antlr4插件，修改SqlBase.g4文件
```
statement
    | SHOW VERSION                     #showVersion

ansiNonReserved
    | VERSION

nonReserved
    | VERSION

VERSION: 'VERSION'
```
