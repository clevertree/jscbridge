# jscbridge Copilot Instructions

## Project Overview
Base library for Android JavaScript/JSC runtime management. Provides extensible foundation for JavaScript execution with CommonJS module system and console bridging to Kotlin.

## Architecture

### Key Components
1. **JSCManager** - Base class for JavaScript runtime management
   - Manages JavaScriptCore context initialization
   - Injects CommonJS module system
   - Provides console shim for logging
   - Extension point: `setupModules(context: JSContext)` for subclass customization

2. **JSContext** - Wrapper for native JSC context
   - `evaluateScript(script, filename)` - Execute JavaScript code
   - Manages garbage collection and cleanup

3. **Module System** - CommonJS-compatible require() function
   - Handles built-in modules: `react`, `@clevertree/meta`
   - Cache support via `__module_cache`
   - Extensible for custom modules

## Build System
- Gradle-based Android library (AAR)
- Published to mavenLocal: `com.clevertree:jscbridge:1.0.0`
- No WASM/web build (Android-only)

## Core Extensibility Pattern

### Making JSCManager Extensible
**CRITICAL: All public/protected methods must be marked `open` in Kotlin for subclasses to override them.**

```kotlin
open class JSCManager(protected val context: Context) {
    // All methods that subclasses may override MUST be marked 'open'
    open fun initialize() { ... }
    open fun setupModules(context: JSContext) { ... }
    open fun getContext(): JSContext? { ... }
    protected open fun injectCommonJSModule(context: JSContext) { ... }
    protected open fun installConsole(context: JSContext) { ... }
}
```

**Why this matters:**
- Subclasses inherit base infrastructure (CommonJS, console)
- Only override `setupModules()` to register custom modules
- Removes code duplication across projects

### Subclass Implementation Pattern
```kotlin
class MyJSCManager(context: Context) : JSCManager(context) {
    init {
        // Do any initialization setup here
        super.initialize()  // Triggers setupModules() override
    }
    
    override fun setupModules(context: JSContext) {
        super.setupModules(context)  // Call parent first
        // Register custom modules
        installMyCustomModule(context)
    }
}
```

## Key Methods

### Public API
- `initialize()` - Set up JSC engine and call setupModules()
- `getContext(): JSContext?` - Get active JSContext for script evaluation
- `evaluateScript(script: String, filename: String): String` - Run JavaScript
- `loadAsset(filename: String): String` - Load and execute asset file
- `cleanup()` - Teardown and cleanup resources

### Protected Methods (for subclass override)
- `setupModules(context: JSContext)` - **Extension point: register custom modules**
- `resetEngine()` - Engine lifecycle management
- `injectCommonJSModule(context: JSContext)` - CommonJS system setup
- `installConsole(context: JSContext)` - Console shim setup

## Publishing & Dependency Management

### Build & Publish
```bash
cd /home/ari/dev/jscbridge
./gradlew clean publishToMavenLocal
```

### When Subclass Build Fails
1. **Clear old version from Maven cache:**
   ```bash
   rm -rf ~/.m2/repository/com/clevertree/jscbridge
   ```

2. **Rebuild and publish jscbridge:**
   ```bash
   cd /home/ari/dev/jscbridge && ./gradlew clean publishToMavenLocal
   ```

3. **Rebuild dependent project with dependency refresh:**
   ```bash
   cd /path/to/dependent-app
   ./gradlew --refresh-dependencies clean assembleDebug
   ```

### Common Error: "This type is final, so it cannot be inherited from"
- **Cause**: Base class methods not marked `open`
- **Solution**: Add `open` keyword to all methods that subclasses override
- **Verify**: Check `JSCManager.kt` for `open` keyword on: `initialize()`, `setupModules()`, `getContext()`, `resetEngine()`, `injectCommonJSModule()`, `installConsole()`

## Testing
Tests located in themed-styler test app at:
`/home/ari/dev/themed-styler/tests/android/app/src/main/java/com/relay/test/`

Build and install:
```bash
cd /home/ari/dev/themed-styler/tests/android
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Verify in logs:
```bash
adb logcat com.relay.test:I -e "JSCManager|HookRenderer"
```

## Implementation Details

### CommonJS Module Registration
When evaluateScript() calls setupModules(), the following modules are available via require():
- `'react'` → returns `globalThis.__react` (set by parent app)
- `'@clevertree/meta'` → returns `globalThis.__relay_meta` with `{ dirname: '/', filename: '/index.js' }`

Subclasses can extend this by calling `context.evaluateScript()` in `setupModules()` to register additional modules in `globalThis.__clevertree_packages`.

### Console Bridging
The console shim captures messages into `globalThis.__console_logs`:
```javascript
console.log('message')  // → globalThis.__console_logs.push('[LOG] message')
```

Subclasses can drain this queue via `jsContext?.evaluateScript("globalThis.__console_logs")`.

## Development Workflow

### After Changes
1. Update or add tests if logic changed
2. Test in consuming project (e.g., themed-styler test app)
3. Run: `./gradlew clean publishToMavenLocal`
4. In consumer: `./gradlew --refresh-dependencies clean assembleDebug`

### Key Files
- `src/main/kotlin/com/clevertree/jscbridge/JSCManager.kt` - Base class (must be open)
- `src/main/kotlin/com/clevertree/jscbridge/JSContext.kt` - JSC wrapper
- `build.gradle` - AAR configuration and publishing

## Troubleshooting

### JSContext methods missing
- Verify JSContext.kt has the method you're calling
- Check for API version mismatches in Gradle dependencies
- Review native binding availability

### Module not found at runtime
- Verify module registered in setupModules() before use
- Check spelling matches exactly (case-sensitive)
- Ensure setupModules() was called by checking logs

### Garbage collection issues
- Call cleanup() before discarding JSCManager instance
- Set jsContext = null after cleanup
- Monitor memory in Android Profiler

## Integration Checklist

When building a new project that uses jscbridge:
- ✅ Add dependency: `com.clevertree:jscbridge:1.0.0` from mavenLocal
- ✅ Create JSCManager subclass extending `com.clevertree.jscbridge.JSCManager`
- ✅ Override `setupModules(context: JSContext)` to register custom modules
- ✅ Call `super.initialize()` in init block AFTER subclass construction
- ✅ Test with `./gradlew --refresh-dependencies clean assembleDebug` (refresh is critical)
- ✅ Verify logs show successful JSC initialization
