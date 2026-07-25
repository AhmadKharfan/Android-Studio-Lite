package com.ahmadkharfan.androidstudiolite.domain.repository

/**
 * The full Git contract, composed of focused role interfaces so that a client can
 * depend on only the slice it needs (e.g. a diff screen on [GitDiffRepository])
 * instead of this whole surface.
 */
interface GitRepository :
    GitLifecycleRepository,
    GitDiffRepository,
    GitStagingRepository,
    GitCommitRepository,
    GitBranchRepository,
    GitHistoryRepository,
    GitTagRepository,
    GitStashRepository,
    GitIntegrationRepository,
    GitRemoteRepository,
    GitSubmoduleRepository
