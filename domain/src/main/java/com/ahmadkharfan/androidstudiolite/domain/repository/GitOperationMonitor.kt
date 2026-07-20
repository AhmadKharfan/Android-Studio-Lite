package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.ActiveOperation
import java.io.File
import kotlinx.coroutines.flow.StateFlow

interface GitOperationMonitor {
    fun activeOperation(repoDir: File): StateFlow<ActiveOperation?>
    fun cancelActiveOperation(repoDir: File): Boolean
}
