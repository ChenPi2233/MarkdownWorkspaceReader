package com.example.mdworkspace.ui.navigation

object Routes {
    const val Setup = "setup?edit={edit}"
    const val Reader = "reader?path={path}"

    fun setup(edit: Boolean = false): String = "setup?edit=$edit"
    fun reader(path: String = ""): String = "reader?path=${android.net.Uri.encode(path)}"
}
