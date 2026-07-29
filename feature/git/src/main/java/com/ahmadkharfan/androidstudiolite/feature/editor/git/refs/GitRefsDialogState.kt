package com.ahmadkharfan.androidstudiolite.feature.editor.git.refs

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitStash
import com.ahmadkharfan.androidstudiolite.domain.model.GitTag

@Stable
class GitRefsDialogState {
    var createOpen by mutableStateOf(false)
    var name by mutableStateOf("")
    var message by mutableStateOf("")
    var includeUntracked by mutableStateOf(false)
    var rename by mutableStateOf<GitBranch?>(null)
    var deleteBranch by mutableStateOf<GitBranch?>(null)
    var mergeBranch by mutableStateOf<GitBranch?>(null)
    var deleteTag by mutableStateOf<GitTag?>(null)
    var dropStash by mutableStateOf<GitStash?>(null)
    var popStash by mutableStateOf<GitStash?>(null)
}
