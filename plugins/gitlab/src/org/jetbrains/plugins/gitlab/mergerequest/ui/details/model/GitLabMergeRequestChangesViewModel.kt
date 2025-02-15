// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.ListSelection
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestChanges

internal interface GitLabMergeRequestChangesViewModel {
  val reviewCommits: StateFlow<List<GitLabCommitDTO>>
  val selectedCommit: StateFlow<GitLabCommitDTO?>
  val selectedCommitIndex: StateFlow<Int>

  val changesResult: Flow<Result<Collection<Change>>>
  val selectedChanges: StateFlow<ListSelection<Change>>

  fun selectCommit(commit: GitLabCommitDTO?)

  fun selectAllCommits()

  fun selectNextCommit()

  fun selectPreviousCommit()

  fun updateSelectedChanges(changes: ListSelection<Change>)

  fun showDiff()
}

internal class GitLabMergeRequestChangesViewModelImpl(
  parentCs: CoroutineScope,
  changes: Flow<GitLabMergeRequestChanges>
) : GitLabMergeRequestChangesViewModel {
  private val cs = parentCs.childScope()

  override val reviewCommits: StateFlow<List<GitLabCommitDTO>> =
    changes.map { it.commits }
      .stateIn(cs, SharingStarted.Lazily, listOf())

  private val _selectedCommit: MutableStateFlow<GitLabCommitDTO?> = MutableStateFlow(null)
  override val selectedCommit: StateFlow<GitLabCommitDTO?> = _selectedCommit.asStateFlow()

  private val _selectedCommitIndex: MutableStateFlow<Int> = MutableStateFlow(0)
  override val selectedCommitIndex: StateFlow<Int> = _selectedCommitIndex.asStateFlow()

  override val changesResult: Flow<Result<Collection<Change>>> =
    combine(changes.map { runCatching { it.getParsedChanges() } }, selectedCommit) { changesResult, commit ->
      changesResult.map {
        it.changesByCommits[commit?.sha] ?: it.changes
      }
    }.modelFlow(cs, thisLogger())

  private val _selectedChangesEvents = MutableSharedFlow<ListSelection<Change>>()
  override val selectedChanges: StateFlow<ListSelection<Change>> =
    _selectedChangesEvents
      .distinctUntilChanged(::isSelectionEqual)
      .stateIn(cs, SharingStarted.Lazily, ListSelection.empty())

  private val _showDiffRequests = MutableSharedFlow<Unit>()
  val showDiffRequests = _showDiffRequests.asSharedFlow()

  override fun selectCommit(commit: GitLabCommitDTO?) {
    _selectedCommit.value = commit
    _selectedCommitIndex.value = reviewCommits.value.indexOf(commit)
  }

  override fun selectAllCommits() {
    _selectedCommit.value = null
    _selectedCommitIndex.value = 0
  }

  override fun selectNextCommit() {
    _selectedCommitIndex.value++
    _selectedCommit.value = reviewCommits.value[_selectedCommitIndex.value]
  }

  override fun selectPreviousCommit() {
    _selectedCommitIndex.value--
    _selectedCommit.value = reviewCommits.value[_selectedCommitIndex.value]
  }

  override fun updateSelectedChanges(changes: ListSelection<Change>) {
    cs.launch {
      _selectedChangesEvents.emit(changes)
    }
  }

  override fun showDiff() {
    cs.launch {
      _showDiffRequests.emit(Unit)
    }
  }

  companion object {
    private fun isSelectionEqual(old: ListSelection<Change>, new: ListSelection<Change>): Boolean {
      if (old.selectedIndex != new.selectedIndex) return false
      val oldList = old.list
      val newList = new.list
      if (oldList.size != newList.size) return false

      return oldList.equalsVia(newList) { o1, o2 ->
        o1 == o2 &&
        o1.beforeRevision == o2.beforeRevision &&
        o1.afterRevision == o2.afterRevision
      }
    }

    private fun <E> List<E>.equalsVia(other: List<E>, isEqual: (E, E) -> Boolean): Boolean {
      if (other === this) return true
      val i1 = listIterator()
      val i2 = other.listIterator()

      while (i1.hasNext() && i2.hasNext()) {
        val e1 = i1.next()
        val e2 = i2.next()
        if (!isEqual(e1, e2)) return false
      }
      return !(i1.hasNext() || i2.hasNext())
    }
  }
}