# Capture SDK Sample Apps

The sample apps demonstrate how to integrate the Socket Mobile Capture SDK into your project.

Please follow the link for documentation on how to integrate the Capture SDK
https://docs.socketmobile.com/capture/java/en/latest/.

# Devices compatibility and CaptureSDK versions
|                    Devices                     |          < 1.4         |          1.4           |          1.5          |          1.6          |         1.7          |        1.8         |        1.9         |
|:----------------------------------------------:| :--------------------: | :--------------------: | :-------------------: | :-------------------: |:-------------------: |:------------------:|:------------------:|
|               **SocketCam C820**               |          ❌            |           ❌           |           ✅           |           ✅          |           ✅          |         ✅          |         ✅          |
|               **S720/D720/S820**               |          ❌            |           ❌           |           ✅           |           ✅          |           ✅          |         ✅          |         ✅          |
| **D600, S550, and all other barcode scanners** |          ❌            |           ✅           |           ✅           |           ✅          |           ✅          |         ✅          |         ✅          |
|                    **S370**                    |          ❌            |           ❌           |           ❌           |           ✅          |           ✅          |         ✅          |         ✅          |
|                    **M930**                    |          ❌            |           ❌           |           ❌           |           ❌          |           ✅          |         ✅          |         ✅          |
|               **SocketCam C860**               |          ❌            |           ❌           |           ❌           |           ❌          |           ✅          |         ✅          |         ✅          |
|                    **S320**                    |          ❌            |           ❌           |           ❌           |           ❌          |           ❌          |         ✅          |         ✅          |
|                    **S721**                    |          ❌            |           ❌           |           ❌           |           ❌          |           ❌          |         ❌          |         ✅          |


# Changes

## HelloCapture 1.9.0
* Release date : Dec 2025
* Add support for S721

## HelloCapture 1.8.57
* Release date : Oct 2025
* Upgrade dependencies for 16KB page size support

## HelloCapture 1.8.47
* Release date : Feb 2025
* Upgrade target sdk to Android 15
* Add a 'result' field to the success response to capture any warnings about the response

## HelloCapture 1.8.36
* Release date : Jan 2025
* Bug fix for DeviceType.isNfcScanner() returning a wrong value

## HelloCapture 1.8.26
* Release date : Oct 2024
* Add sdk version when opening a client

## HelloCapture 1.8.21
* Release date : Oct 2024
* New factory method for creating custom view

## HelloCapture 1.8.13
* Release date : Jul 2024
* Update sdk which targets api level 34

## HelloCapture 1.8.9
* Release date : Jun 2024
* Support custom view for Socketcam
* Support front camera for Socketcam

## HelloCapture 1.8.4/1.8.5
* Release date : Mar 2024
* Minor UI updates

## HelloCapture 1.8.2
* Release date : Mar 2024
* Support S320
* Update Broadcast Receiver flags

## HelloCapture 1.7.30
* Release date : Nov 2023
* Upgrade to C860

## HelloCapture 1.6.20
* Release date : Nov 2023
* Patch fix to start Companion Service after Companion is upgraded to Android 12+

## HelloCapture 1.6.15
* Release date : Feb 2023
* Support S370

## HelloCapture 1.6.5
* Release date : Jan 2023
* Patch fix to compile on Android 12

## HelloCapture 1.6.3
* Release date : Nov 2022
* Support for SocketCam C820

