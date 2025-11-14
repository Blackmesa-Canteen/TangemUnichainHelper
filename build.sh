#!/bin/bash
# Simple build script for Tangem Unichain App

echo "================================"
echo "Tangem Unichain App Build Script"
echo "================================"
echo ""

# Check if gradlew exists
if [ ! -f "./gradlew" ]; then
    echo "❌ Error: gradlew not found. Are you in the project root directory?"
    exit 1
fi

# Make gradlew executable
chmod +x ./gradlew

echo "Select build type:"
echo "1) Debug (for testing)"
echo "2) Release (for production)"
echo ""
read -p "Enter choice (1 or 2): " choice

case $choice in
    1)
        echo ""
        echo "🔨 Building debug APK..."
        ./gradlew assembleDebug

        if [ $? -eq 0 ]; then
            echo ""
            echo "✅ Build successful!"
            echo "📦 APK location: app/build/outputs/apk/debug/app-debug.apk"
            echo ""
            echo "To install on connected device:"
            echo "  adb install app/build/outputs/apk/debug/app-debug.apk"
        else
            echo ""
            echo "❌ Build failed. Check errors above."
            exit 1
        fi
        ;;
    2)
        echo ""
        echo "🔨 Building release APK..."
        ./gradlew assembleRelease

        if [ $? -eq 0 ]; then
            echo ""
            echo "✅ Build successful!"
            echo "📦 APK location: app/build/outputs/apk/release/app-release-unsigned.apk"
            echo ""
            echo "⚠️  Note: This APK is unsigned. For production, you should sign it."
        else
            echo ""
            echo "❌ Build failed. Check errors above."
            exit 1
        fi
        ;;
    *)
        echo ""
        echo "❌ Invalid choice. Please enter 1 or 2."
        exit 1
        ;;
esac

echo ""
echo "================================"
echo "Build Complete!"
echo "================================"