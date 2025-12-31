package com.clevertree.jscbridge

import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson

/**
 * Generic JSC Manager
 * Manages JavaScriptCore runtime and provides hooks for module registration
 */
class JSCManager(private val context: Context) {
    companion object {
        private const val TAG = "JSCManager"
        var activeManager: JSCManager? = null
    }

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var jsContext: JSContext? = null
    var userMessageHandler: ((String, Boolean) -> Unit)? = null
    private val moduleInitializers = mutableListOf<(JSContext) -> Unit>()

    fun initialize() {
        resetEngine()
    }

    fun addModuleInitializer(initializer: (JSContext) -> Unit) {
        moduleInitializers.add(initializer)
        jsContext?.let { initializer(it) }
    }

    fun getContext(): JSContext? = jsContext

    private fun resetEngine() {
        jsContext = null
        activeManager = this

        try {
            jsContext = JSContext(JSContext.create()).also { context ->
                Log.i(TAG, "Starting JSC engine")
                
                installConsole(context)
                injectCommonJSModule(context)
                
                // Run all registered module initializers
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

    private fun injectCommonJSModule(context: JSContext) {
        val moduleCode = """
            var globalObj = (typeof globalThis !== 'undefined') ? globalThis : (typeof window !== 'undefined' ? window : this);
            globalObj.module = { exports: {} };
            globalObj.exports = globalObj.module.exports;
            globalObj.__module_cache = {};
            
            globalObj.require = function(id) {
                if (globalObj.__module_cache[id]) return globalObj.__module_cache[id];
                if (id === 'react') return globalObj.__react || {};
                throw new Error('Module not found: ' + id);
            };
        """.trimIndent()
        
        try {
            context.evaluateScript(moduleCode, "commonjs_init.js")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject CommonJS module system", e)
        }
    }

    private fun installConsole(context: JSContext) {
        val consoleCode = """
            (function() {
                if (!globalThis.__console_logs) globalThis.__console_logs = [];
                globalThis.console = {
                    log: function(...args) {
                        const msg = args.map(String).join(' ');
                        globalThis.__console_logs.push('[LOG] ' + msg);
                    },
                    warn: function(...args) {
                        const msg = args.map(String).join(' ');
                        globalThis.__console_logs.push('[WARN] ' + msg);
                    },
                    error: function(...args) {
                        const msg = args.map(String).join(' ');
                        globalThis.__console_logs.push('[ERROR] ' + msg);
                    },
                    info: function(...args) {
                        const msg = args.map(String).join(' ');
                        globalThis.__console_logs.push('[INFO] ' + msg);
                    }
                };
            })();
        """.trimIndent()
        
        context.evaluateScript(consoleCode, "console_shim.js")
    }

    fun evaluateScript(script: String, filename: String = "script.js"): String {
        return jsContext?.evaluateScript(script, filename) ?: ""
    }

    fun loadAsset(filename: String) {
        try {
            val source = this.context.assets.open(filename).bufferedReader().use { it.readText() }
            jsContext?.evaluateScript(source, filename)
            Log.d(TAG, "Loaded asset: $filename")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load asset: $filename", e)
        }
    }

    fun cleanup() {
        jsContext = null
    }
}
