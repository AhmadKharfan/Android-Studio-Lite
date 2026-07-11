package com.example.androidstudiolite.feature.uidesigner

interface DesignerInteractionListener {
    fun onTabSelected(tab: DesignerTab)
    fun onIdChanged(value: String)
    fun onTextChanged(value: String)
    fun onLayoutWidthChanged(value: String)
    fun onLayoutHeightChanged(value: String)
}
