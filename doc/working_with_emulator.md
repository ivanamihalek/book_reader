# Emulator Storage Location

The Android Virtual Devices (AVDs, a.k.a. emulators), 
are stored in a specific directory on your desktop. The location varies depending on your operating system:

* Windows:` C:\Users\<username>\.android\avd\`
* macOS: `~/.android/avd/`
* Linux:` ~/.android/avd/`

Each AVD has its own directory, containing files like the emulator's system (disk) image, user data, 
and configuration files. For example:
```bash
bash> pwd
/home/ivana/.android/avd
bash> ls
Medium_Tablet.avd  Medium_Tablet.ini  Pixel_3a_API_33_x86_64.avd  Pixel_3a_API_33_x86_64.ini
```


# Starting an emulator

From Android Studio, navigate to "Device Manager" (a small icon on the right with the stylized phone
and android logo) and click on the start icon.

Alternatively, you can start Android emulators without Android Studio using command-line tools. Here's how:

## Using the `emulator` command

**First, locate your Android SDK:**
- **Windows:** Usually `C:\Users\[Username]\AppData\Local\Android\Sdk\`
- **macOS:** Usually `~/Library/Android/sdk/`
- **Linux:** Usually `~/Android/Sdk/`

**Add emulator to your PATH or navigate to:**
```bash
[SDK_PATH]/emulator/
```

**List available AVDs:**
```bash
emulator -list-avds
```

**Start a specific emulator:**
```bash
emulator -avd [AVD_NAME]
```

**Example:**
```bash
emulator -avd Pixel_4_API_30
```

## Common startup options

**Start with specific options:**
```bash
# Start with writable system partition
emulator -avd MyAVD -writable-system

# Start with specific RAM allocation
emulator -avd MyAVD -memory 2048

# Start without boot animation (faster startup)
emulator -avd MyAVD -no-boot-anim

# Start in headless mode (no GUI)
emulator -avd MyAVD -no-window

# Start with specific GPU settings
emulator -avd MyAVD -gpu host
```

##  Creating a shortcut/script

**Windows batch file:**
```batch
@echo off
cd /d "C:\Users\[Username]\AppData\Local\Android\Sdk\emulator"
emulator.exe -avd Pixel_4_API_30
```

**macOS/Linux script:**
```bash
#!/bin/bash
cd ~/Library/Android/sdk/emulator  # macOS path
./emulator -avd Pixel_4_API_30
```

## Important Notes:

1. **The emulator runs independently** - you can close Android Studio and the emulator will continue running
2. **ADB still works** - you can still use `adb devices` and all ADB commands
3. **Performance may be better** - sometimes emulators run faster when not launched through Android Studio
4. **You can run multiple emulators** simultaneously using different AVD names

This is particularly useful for testing, automation, or when you want to use the emulator without the overhead of the full IDE.

Try: open the emulator first, then Studio. Under "Device Manager" you should find your device running.

# Communicating with the emulated device

1. **List running emulators:**
   ```bash
   adb devices
   ```

2. **Connect to a specific emulator:**
   ```bash
   adb -s emulator-5554 shell
   ```

3. **Common ADB commands work normally:**
   ```bash
   adb push localfile.txt /sdcard/
   adb pull /sdcard/remotefile.txt
   adb install app.apk
   adb logcat
   ```

The emulator appears as a device (usually `emulator-5554`, `emulator-5556`, etc.) in your ADB device list.

## File Persistence

**Files in `/sdcard` persist!** 

- Files stored in `/sdcard` (which maps to `/storage/emulated/0/`) are saved in the emulator's userdata partition
- These files survive when you:
  - Close Android Studio
  - Stop and restart the emulator
  - Reboot your computer

**Files are only lost when you:**
- Wipe the emulator data (Cold Boot or Wipe Data)
- Delete the AVD entirely
- Manually delete them

The emulator maintains persistent storage just like a real Android device, so your app data, downloaded files, 
and anything stored in the virtual SD card will remain between sessions.