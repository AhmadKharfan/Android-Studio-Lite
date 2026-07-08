package com.example.androidstudiolite.di

import com.example.androidstudiolite.data.fake.FakeAiAgentRepository
import com.example.androidstudiolite.data.fake.FakeAiChatRepository
import com.example.androidstudiolite.data.fake.FakeFileSystemRepository
import com.example.androidstudiolite.data.fake.FakeFileTreeRepository
import com.example.androidstudiolite.data.fake.FakeGitRepository
import com.example.androidstudiolite.data.fake.FakeIdeConfigRepository
import com.example.androidstudiolite.data.fake.FakeOnboardingRepository
import com.example.androidstudiolite.data.fake.FakePreferencesRepository
import com.example.androidstudiolite.data.fake.FakeProjectRepository
import com.example.androidstudiolite.data.fake.FakeTemplateRepository
import com.example.androidstudiolite.data.fake.FakeTerminalRepository
import com.example.androidstudiolite.domain.repository.AiAgentRepository
import com.example.androidstudiolite.domain.repository.AiChatRepository
import com.example.androidstudiolite.domain.repository.FileSystemRepository
import com.example.androidstudiolite.domain.repository.FileTreeRepository
import com.example.androidstudiolite.domain.repository.GitRepository
import com.example.androidstudiolite.domain.repository.IdeConfigRepository
import com.example.androidstudiolite.domain.repository.OnboardingRepository
import com.example.androidstudiolite.domain.repository.PreferencesRepository
import com.example.androidstudiolite.domain.repository.ProjectRepository
import com.example.androidstudiolite.domain.repository.TemplateRepository
import com.example.androidstudiolite.domain.repository.TerminalRepository

/**
 * Process-wide singleton container for repositories. A lightweight manual substitute for a DI
 * framework — swap any `Fake*Repository` for a real implementation here without touching
 * domain or UI code.
 */
object AppContainer {
    val onboardingRepository: OnboardingRepository by lazy { FakeOnboardingRepository() }
    val projectRepository: ProjectRepository by lazy { FakeProjectRepository() }
    val templateRepository: TemplateRepository by lazy { FakeTemplateRepository() }
    val fileTreeRepository: FileTreeRepository by lazy { FakeFileTreeRepository() }
    val preferencesRepository: PreferencesRepository by lazy { FakePreferencesRepository() }
    val aiAgentRepository: AiAgentRepository by lazy { FakeAiAgentRepository() }
    val fileSystemRepository: FileSystemRepository by lazy { FakeFileSystemRepository() }
    val ideConfigRepository: IdeConfigRepository by lazy { FakeIdeConfigRepository() }
    val terminalRepository: TerminalRepository by lazy { FakeTerminalRepository() }
    val gitRepository: GitRepository by lazy { FakeGitRepository() }
    val aiChatRepository: AiChatRepository by lazy { FakeAiChatRepository() }
}
