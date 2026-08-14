package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.util.WeakHashMap
import java.lang.reflect.Array as ReflectArray

private const val CUSTOM_DUSK_OVERRIDE_TAG = "CustomDuskColors"
private const val COLOR_RESOURCES_LOADER_CREATOR_CLASS =
    "com.google.android.material.color.ColorResourcesLoaderCreator"

private data class CustomDuskLoaderState(
    val loader: Any,
    val colors: Map<Int, Int>,
)

// A Resources instance gets at most one Custom Dusk loader. Re-applying the same palette is a
// no-op; changing the palette replaces the previous loader instead of stacking another one.
// Weak keys ensure dead Resources instances are not retained by this bookkeeping.
private val customDuskLoaders = WeakHashMap<Resources, CustomDuskLoaderState>()

@RequiresApi(Build.VERSION_CODES.R)
fun AppCompatActivity.applyCustomDuskColorResources(palette: CustomDuskPalette): Boolean =
    applyColorResourceOverrides(this, palette.asColorResourceMap())

@RequiresApi(Build.VERSION_CODES.R)
fun AppCompatActivity.clearCustomDuskColorResources() {
    val targetResources = resources
    synchronized(customDuskLoaders) {
        val existing = customDuskLoaders.remove(targetResources) ?: return
        runCatching { invokeResourcesLoaderMethod(targetResources, "removeLoaders", existing.loader) }
            .onFailure { error ->
                Log.w(CUSTOM_DUSK_OVERRIDE_TAG, "Unable to remove Custom Dusk color resources", error)
            }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun applyColorResourceOverrides(
    context: Context,
    colors: Map<Int, Int>,
): Boolean {
    val targetResources = context.resources
    val stableColors = colors.toMap()

    synchronized(customDuskLoaders) {
        val existing = customDuskLoaders[targetResources]
        if (existing?.colors == stableColors) return true

        val newLoader = createResourcesLoader(context, stableColors) ?: return false

        return runCatching {
            // Material's ColorResourcesOverride also installs a personalized-colors theme
            // overlay. Custom Dusk only needs the generated resource table, so install that
            // loader directly and leave the Midnight Dusk theme hierarchy untouched.
            existing?.let { invokeResourcesLoaderMethod(targetResources, "removeLoaders", it.loader) }
            invokeResourcesLoaderMethod(targetResources, "addLoaders", newLoader)
            customDuskLoaders[targetResources] = CustomDuskLoaderState(newLoader, stableColors)
            true
        }.getOrElse { error ->
            // If replacement failed after removing the old loader, make a best effort to put it
            // back so the current activity remains usable.
            if (existing != null) {
                runCatching { invokeResourcesLoaderMethod(targetResources, "addLoaders", existing.loader) }
                customDuskLoaders[targetResources] = existing
            }
            Log.w(CUSTOM_DUSK_OVERRIDE_TAG, "Unable to install Custom Dusk color resources", error)
            false
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun createResourcesLoader(
    context: Context,
    colors: Map<Int, Int>,
): Any? =
    runCatching {
        val creatorClass = Class.forName(COLOR_RESOURCES_LOADER_CREATOR_CLASS)
        val createMethod =
            creatorClass.declaredMethods
                .first {
                    it.name == "create" && it.parameterCount == 2
                }.apply { isAccessible = true }

        createMethod.invoke(null, context, colors)
    }.getOrElse { error ->
        Log.w(CUSTOM_DUSK_OVERRIDE_TAG, "Unable to create Custom Dusk color resource loader", error)
        null
    }

@RequiresApi(Build.VERSION_CODES.R)
private fun invokeResourcesLoaderMethod(
    resources: Resources,
    methodName: String,
    loader: Any,
) {
    val method =
        Resources::class.java.methods.first {
            it.name == methodName &&
                it.parameterCount == 1 &&
                it.parameterTypes[0].isArray
        }
    val loaderType = method.parameterTypes[0].componentType
    val loaders = ReflectArray.newInstance(loaderType, 1)
    ReflectArray.set(loaders, 0, loader)
    method.invoke(resources, loaders)
}
