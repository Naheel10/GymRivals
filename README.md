# GymRivals

GymRivals is an Android fitness app built for a Mobile App Development group project. It combines workout logging, progress tracking, and friendly competition so users can stay consistent and motivated.

## What this app does
- **Tracks multiple workout types**: strength sessions, outdoor runs, and camera-based rep workouts.
- **Supports social motivation**: includes a rivals flow, profile area, and competitive progress mindset.
- **Shows progress over time**: workout history, streak-style engagement, and progress-oriented views.

## Feature highlights
- Strength workout logging
- GPS run tracking with Google Maps
- Push-up / squat rep counting using **ML Kit Pose Detection + CameraX**
- Google Sign-In + Firebase Authentication
- Firestore-backed cloud data (history and activity records)
- Multi-tab Jetpack Compose UI (Home, Log, Progress, Rivals, Profile)

## Tech stack
- **Kotlin** + **Jetpack Compose**
- **Android Navigation Compose**
- **Google Maps / Location Services**
- **Firebase Auth + Firestore**
- **ML Kit Pose Detection**
- **CameraX**

## App preview
> I could not run an Android emulator in this environment because Gradle/Kotlin tooling here is using JDK 25, which is incompatible with this project setup. I included the app icon as a visual preview for now.

![GymRivals app icon](app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp)

## Running locally (Android Studio)
1. Open the project in Android Studio.
2. Use **JDK 17** (recommended for this Gradle/Kotlin setup).
3. Sync Gradle and build the app.
4. Run on an emulator or Android device (API 31+).

## Why this project matters
This project demonstrates practical, end-to-end mobile development in a team setting: modern Android UI, cloud integration, device sensors/camera, location services, and feature design aimed at real user motivation.
