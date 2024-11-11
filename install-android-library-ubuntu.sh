#!/bin/bash

apt-get install -qq -y unzip
apt-get update -qq -y

mkdir templibs
mkdir templibs/hybrid-pqc
mkdir templibs/mobile-go

mkdir templibs/hybrid-pqc/android-arm64-v8a
mkdir templibs/hybrid-pqc/android-armeabi-v7a
mkdir templibs/hybrid-pqc/android-x86
mkdir templibs/hybrid-pqc/android-x86_64

mkdir templibs/mobile-go/android-arm64-v8a
mkdir templibs/mobile-go/android-armeabi-v7a
mkdir templibs/mobile-go/android-x86
mkdir templibs/mobile-go/android-x86_64

sudo chmod -R a+rwx $PWD/app/src/main/jniLibs/arm64-v8a
sudo chmod -R a+rwx $PWD/app/src/main/jniLibs/armeabi-v7a
sudo chmod -R a+rwx $PWD/app/src/main/jniLibs/x86
sudo chmod -R a+rwx $PWD/app/src/main/jniLibs/x86_64

sudo rm -r $PWD/app/src/main/jniLibs/arm64-v8a/*
sudo rm -r $PWD/app/src/main/jniLibs/armeabi-v7a/*
sudo rm -r $PWD/app/src/main/jniLibs/x86/*
sudo rm -r $PWD/app/src/main/jniLibs/x86_64/*

curl -Lo $PWD/templibs/hybrid-pqc/includes.zip https://github.com/quantumcoinproject/hybrid-pqc/releases/download/v0.1.42/includes.zip
unzip $PWD/templibs/hybrid-pqc/includes.zip -d $PWD/templibs/hybrid-pqc
echo "E6DE4035B5DCA73970A73F802CCC25894B59056EB48533CCCED530A7ECC46DEC $PWD/templibs/hybrid-pqc/includes.zip" | sha256sum --check  - || exit 1

curl -Lo $PWD/templibs/hybrid-pqc/android-arm64-v8a.tar.gz https://github.com/quantumcoinproject/hybrid-pqc/releases/download/v0.1.42/android-arm64-v8a.tar.gz
tar -zxf $PWD/templibs/hybrid-pqc/android-arm64-v8a.tar.gz --directory $PWD/templibs/hybrid-pqc//android-arm64-v8a
echo "5fd4926f1b7c3f2f4519860fee38c43de65f47d31aaab7701853e29b535a9570 $PWD/templibs/hybrid-pqc/android-arm64-v8a.tar.gz" | sha256sum --check  - || exit 1

curl -Lo $PWD/templibs/hybrid-pqc/android-armeabi-v7a.tar.gz https://github.com/quantumcoinproject/hybrid-pqc/releases/download/v0.1.42/android-armeabi-v7a.tar.gz
tar -zxf $PWD/templibs/hybrid-pqc/android-armeabi-v7a.tar.gz --directory $PWD/templibs/hybrid-pqc/android-armeabi-v7a
echo "7005851c18db74e1aafc806f2d28635fb9839b349ebd61ea50f4904cf6c9eae7 $PWD/templibs/hybrid-pqc/android-armeabi-v7a.tar.gz" | sha256sum --check  - || exit 1

curl -Lo $PWD/templibs/hybrid-pqc/android-x86.tar.gz https://github.com/quantumcoinproject/hybrid-pqc/releases/download/v0.1.42/android-x86.tar.gz
tar -zxf $PWD/templibs/hybrid-pqc/android-x86.tar.gz --directory $PWD/templibs/hybrid-pqc/android-x86
echo "f1268751de443aaf656012a68fea8e000944db0c99863f4bf33c126cdce8a5cc $PWD/templibs/hybrid-pqc/android-x86.tar.gz" | sha256sum --check  - || exit 1

curl -Lo $PWD/templibs/hybrid-pqc/android-x86_64.tar.gz https://github.com/quantumcoinproject/hybrid-pqc/releases/download/v0.1.42/android-x86_64.tar.gz
tar -zxf $PWD/templibs/hybrid-pqc/android-x86_64.tar.gz --directory $PWD/templibs/hybrid-pqc/android-x86_64
echo "05a6d3b76cb22dc173a35d730568be047437062b00d9bc7fc8b29531b1865b37 $PWD/templibs/hybrid-pqc/android-x86_64.tar.gz" | sha256sum --check  - || exit 1

sudo cp $PWD/templibs/hybrid-pqc/build/include/dilithium/hybrid.h $PWD/app/src/main/jniLibs/arm64-v8a/hybrid.h
sudo cp $PWD/templibs/hybrid-pqc/build/include/dilithium/hybrid.h $PWD/app/src/main/jniLibs/armeabi-v7a/hybrid.h
sudo cp $PWD/templibs/hybrid-pqc/build/include/dilithium/hybrid.h $PWD/app/src/main/jniLibs/x86/hybrid.h
sudo cp $PWD/templibs/hybrid-pqc/build/include/dilithium/hybrid.h $PWD/app/src/main/jniLibs/x86_64/hybrid.h

sudo cp $PWD/templibs/hybrid-pqc/build/include/common/randombytes.h $PWD/app/src/main/jniLibs/arm64-v8a/randombytes.h
sudo cp $PWD/templibs/hybrid-pqc/build/include/common/randombytes.h $PWD/app/src/main/jniLibs/armeabi-v7a/randombytes.h
sudo cp $PWD/templibs/hybrid-pqc/build/include/common/randombytes.h $PWD/app/src/main/jniLibs/x86/randombytes.h
sudo cp $PWD/templibs/hybrid-pqc/build/include/common/randombytes.h $PWD/app/src/main/jniLibs/x86_64/randombytes.h

sudo cp $PWD/templibs/hybrid-pqc/build/include/common/shake_prng.h $PWD/app/src/main/jniLibs/arm64-v8a/shake_prng.h
sudo cp $PWD/templibs/hybrid-pqc/build/include/common/shake_prng.h $PWD/app/src/main/jniLibs/armeabi-v7a/shake_prng.h
sudo cp $PWD/templibs/hybrid-pqc/build/include/common/shake_prng.h $PWD/app/src/main/jniLibs/x86/shake_prng.h
sudo cp $PWD/templibs/hybrid-pqc/build/include/common/shake_prng.h $PWD/app/src/main/jniLibs/x86_64/shake_prng.h

sudo cp $PWD/templibs/hybrid-pqc/build/include/common/fips202.h $PWD/app/src/main/jniLibs/arm64-v8a/fips202.h
sudo cp $PWD/templibs/hybrid-pqc/build/include/common/fips202.h $PWD/app/src/main/jniLibs/armeabi-v7a/fips202.h
sudo cp $PWD/templibs/hybrid-pqc/build/include/common/fips202.h $PWD/app/src/main/jniLibs/x86/fips202.h
sudo cp $PWD/templibs/hybrid-pqc/build/include/common/fips202.h $PWD/app/src/main/jniLibs/x86_64/fips202.h

sudo cp $PWD/templibs/hybrid-pqc/android-arm64-v8a/libhybridpqc.so $PWD/app/src/main/jniLibs/arm64-v8a/libhybridpqc.so
sudo cp $PWD/templibs/hybrid-pqc/android-armeabi-v7a/libhybridpqc.so $PWD/app/src/main/jniLibs/armeabi-v7a/libhybridpqc.so
sudo cp $PWD/templibs/hybrid-pqc/android-x86/libhybridpqc.so $PWD/app/src/main/jniLibs/x86/libhybridpqc.so
sudo cp $PWD/templibs/hybrid-pqc/android-x86_64/libhybridpqc.so $PWD/app/src/main/jniLibs/x86_64/libhybridpqc.so

curl -Lo $PWD/templibs/mobile-go/android-arm64-v8a.tar.gz https://github.com/quantumcoinproject/quantum-coin-go/releases/download/v2.0.53/android-arm64-v8a.tar.gz
tar -zxf $PWD/templibs/mobile-go/android-arm64-v8a.tar.gz --directory $PWD/templibs/mobile-go/android-arm64-v8a
echo "7e1156d5dc47d60cfcc80b526e85bce135911a1a52ce38d87e8ffb6968ea1892 $PWD/templibs/mobile-go/android-arm64-v8a.tar.gz" | sha256sum --check  - || exit 1

curl -Lo $PWD/templibs/mobile-go/android-armeabi-v7a.tar.gz https://github.com/quantumcoinproject/quantum-coin-go/releases/download/v2.0.53/android-armeabi-v7a.tar.gz
tar -zxf $PWD/templibs/mobile-go/android-armeabi-v7a.tar.gz --directory $PWD/templibs/mobile-go/android-armeabi-v7a
echo "825b3b98620b04cd36338515b18d35897a55cc160a0ce7a64d3a4036cec73838 $PWD/templibs/mobile-go/android-armeabi-v7a.tar.gz" | sha256sum --check  - || exit 1

curl -Lo $PWD/templibs/mobile-go/android-x86.tar.gz https://github.com/quantumcoinproject/quantum-coin-go/releases/download/v2.0.53/android-x86.tar.gz
tar -zxf $PWD/templibs/mobile-go/android-x86.tar.gz --directory $PWD/templibs/mobile-go/android-x86
echo "be96a9d829b6b55496dbe37df076da6f5f9fa00273319011c4d00da8660c09a3 $PWD/templibs/mobile-go/android-x86.tar.gz" | sha256sum --check  - || exit 1

curl -Lo $PWD/templibs/mobile-go/android-x86_64.tar.gz https://github.com/quantumcoinproject/quantum-coin-go/releases/download/v2.0.53/android-x86_64.tar.gz
tar -zxf $PWD/templibs/mobile-go/android-x86_64.tar.gz --directory $PWD/templibs/mobile-go/android-x86_64
echo "44d96a9af49bbd634242a5e8d2f652e82ec2686731d81c6a76928e08979d0151 $PWD/templibs/mobile-go/android-x86_64.tar.gz" | sha256sum --check  - || exit 1

sudo cp $PWD/templibs/mobile-go/android-arm64-v8a/* $PWD/app/src/main/jniLibs/arm64-v8a/
sudo cp $PWD/templibs/mobile-go/android-armeabi-v7a/* $PWD/app/src/main/jniLibs/armeabi-v7a/
sudo cp $PWD/templibs/mobile-go/android-x86/* $PWD/app/src/main/jniLibs/x86/
sudo cp $PWD/templibs/mobile-go/android-x86_64/* $PWD/app/src/main/jniLibs/x86_64/
