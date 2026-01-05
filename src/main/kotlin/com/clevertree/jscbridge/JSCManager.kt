package com.clevertree.jscbridge

import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson

/**
 * Generic JSC Manager - Base class for JavaScript runtime management
 * Manages JavaScriptCore runtime with CommonJS module system and console support
 * 
 * Subclasses should:
 * 1. Call initialize() to set up the engine
 * 2. Override setupModules() to register custom modules/bridges
 * 3. Use evaluateScript() to run JavaScript code
 */
open class JSCManager(protected val context: Context) {
    companion object {
        private const val TAG = "JSCManager"
        var activeManager: JSCManager? = null
    }

    protected val gson = Gson()
    protected val mainHandler = Handler(Looper.getMainLooper())
    protected var jsContext: JSContext? = null
    open var userMessageHandler: ((String, Boolean) -> Unit)? = null
    private val moduleInitializers = mutableListOf<(JSContext) -> Unit>()

    open fun initialize() {
        resetEngine()
    }

    protected fun addModuleInitializer(initializer: (JSContext) -> Unit) {
        moduleInitializers.add(initializer)
        jsContext?.let { initializer(it) }
    }

    open fun getContext(): JSContext? = jsContext

    open fun garbageCollect() {
        jsContext?.garbageCollect()
    }

    open fun setupModules(context: JSContext) {
        // Override in subclasses to register custom modules
    }

    /**
     * Register a virtual module that can be required via require(name)
     */
    fun registerModule(name: String, moduleObject: String) {
        val script = "globalThis.__clevertree_packages['$name'] = $moduleObject;"
        jsContext?.evaluateScript(script, "register_module_$name.js")
    }

    protected open fun resetEngine() {
        jsContext = null
        activeManager = this

        try {
            jsContext = JSContext(JSContext.create()).also { context ->
                Log.i(TAG, "Starting JSC engine")
                
                installConsole(context)
                injectCommonJSModule(context)
                
                // Run setup and custom initializers
                setupModules(context)
                moduleInitializers.forEach { it(context) }
                
                Log.i(TAG, "JSC engine initialized")
            }
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to initialize JSC native bindings", e)
            userMessageHandler?.invoke("JSC native bindings missing: ${e.message}", true)
            jsContext = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize JSC engine", e)
            userMessageHandler?.invoke("Failed to initialize JSC: ${e.message}", true)
            jsContext = null
        }
    }

    protected open fun injectCommonJSModule(context: JSContext) {
        val moduleCode = """
            var globalObj = (typeof globalThis !== 'undefined') ? globalThis : (typeof window !== 'undefined' ? window : this);
            globalObj.module = { exports: {} };
            globalObj.exports = globalObj.module.exports;
            globalObj.__module_cache = {};
            globalObj.__clevertree_packages = globalObj.__clevertree_packages || {};
            
            globalObj.require = function(id) {
                if (globalObj.__module_cache[id]) return globalObj.__module_cache[id];
                
                // Check virtual packages first
                if (globalObj.__clevertree_packages[id]) {
                    return globalObj.__clevertree_packages[id];
                }
                
                // Legacy fallbacks
                if (id === 'react') return globalObj.__react || {};
                if (id === '@clevertree/meta') {
                    return globalObj.__relay_meta || { dirname: '/', filename: '/index.js' };
                }
                
                throw new Error('Module not found: ' + id);
            };
        """.trimIndent()
        
        try {
            context.evaluateScript(moduleCode, "commonjs_init.js")
            Log.d(TAG, "Injected CommonJS module system with virtual package support")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject CommonJS module system", e)
        }
    }

    protected open fun installConsole(context: JSContext) {
        context.setObjectForKey("__native_log", object : JavaScriptObject() {
            override fun call(arg: String) {
                Log.i("JSC-Console", arg)
            }
        })

        val consoleCode = """
            (function() {
                if (!globalThis.__console_logs) globalThis.__console_logs = [];
                globalThis.console = {
                    log: function(...args) {
                        const msg = args.map(String).join(' ');
                        globalThis.__console_logs.push('[LOG] ' + msg);
                        if (globalThis.__native_log) globalThis.__native_log('[LOG] ' + msg);
                    },
                    warn: function(...args) {
                        const msg = args.map(String).join(' ');
                        globalThis.__console_logs.push('[WARN] ' + msg);
                        if (globalThis.__native_log) globalThis.__native_log('[WARN] ' + msg);
                    },
                    error: function(...args) {
                        const msg = args.map(String).join(' ');
                        globalThis.__console_logs.push('[ERROR] ' + msg);
                        if (globalThis.__native_log) globalThis.__native_log('[ERROR] ' + msg);
                    },
                    info: function(...args) {
                        const msg = args.map(String).join(' ');
                        globalThis.__console_logs.push('[INFO] ' + msg);
                        if (globalThis.__native_log) globalThis.__native_log('[INFO] ' + msg);
                    }
                };
            })();
        """.trimIndent()
        
        context.evaluateScript(consoleCode, "console_shim.js")
    }

    fun evaluateScript(script: String, filename: String = "script.js"): String {
        return jsContext?.evaluateScript(script, filename) ?: ""
    }

    fun loadAsset(filename: String): String {
        return try {
            val source = context.assets.open(filename).bufferedReader().use { it.readText() }
            jsContext?.evaluateScript(source, filename) ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load asset: $filename", e)
            ""
        }
    }

    open fun cleanup() {
        jsContext = null
    }
}
