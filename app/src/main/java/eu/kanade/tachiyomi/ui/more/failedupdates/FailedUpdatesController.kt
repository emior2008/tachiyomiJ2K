package eu.kanade.tachiyomi.ui.more.failedupdates

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.LibraryManga
import eu.kanade.tachiyomi.data.library.LibraryUpdateFailureStore
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.databinding.FailedUpdatesControllerBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.materialAlertDialog
import eu.kanade.tachiyomi.util.system.notificationManager
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.scrollViewWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class FailedUpdatesController : BaseController<FailedUpdatesControllerBinding>() {
    private val db: DatabaseHelper = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()
    private val failureStore: LibraryUpdateFailureStore = Injekt.get()

    private var adapter: FailedUpdatesAdapter? = null
    private var loadedMangas = emptyMap<Long, LibraryManga>()
    private var retryStartedFromThisScreen = false
    private var updatingSelectAll = false
    private var bottomInset = 0

    override fun getTitle(): String? = activity?.getString(R.string.failed_updates)

    override fun createBinding(inflater: LayoutInflater) = FailedUpdatesControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        adapter = FailedUpdatesAdapter(::onSelectionChanged, ::openSourceWebView)
        binding.recycler.layoutManager = LinearLayoutManager(view.context)
        binding.recycler.adapter = adapter
        binding.recycler.setHasFixedSize(true)

        val fabBaseMarginBottom = binding.retrySelected.marginBottom
        scrollViewWith(
            binding.recycler,
            true,
            afterInsets = { insets ->
                bottomInset = insets.getInsets(systemBars()).bottom
                binding.retrySelected.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = bottomInset + fabBaseMarginBottom
                }
                updateRecyclerBottomPadding()
            },
        )

        binding.selectAll.setOnCheckedChangeListener { _, checked ->
            if (!updatingSelectAll) {
                adapter?.selectAll(checked)
            }
        }

        binding.retrySelected.setOnClickListener {
            retrySelected()
        }
        binding.retrySelected.isInvisible = true

        LibraryUpdateJob.updateFlow
            .onEach { mangaId ->
                if (mangaId == LibraryUpdateJob.STARTING_UPDATE_SOURCE) {
                    loadFailures()
                } else if (mangaId == null) {
                    retryStartedFromThisScreen = false
                    loadFailures()
                }
            }.launchIn(viewScope)

        loadFailures()
    }

    override fun onDestroyView(view: View) {
        binding.recycler.adapter = null
        adapter = null
        super.onDestroyView(view)
    }

    override fun onCreateOptionsMenu(
        menu: Menu,
        inflater: MenuInflater,
    ) {
        inflater.inflate(R.menu.failed_updates, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_clear_failed_updates) {
            showClearConfirmation()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadFailures() {
        if (!isBindingInitialized) return
        binding.progress.isVisible = true
        val context = view?.context ?: return
        viewScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    val library = db.getLibraryMangas().executeAsBlocking().distinctBy { it.id }
                    val libraryById = library.mapNotNull { manga -> manga.id?.let { it to manga } }.toMap()
                    val failures = failureStore.getAll()
                    val validFailures = failures.filter { it.mangaId in libraryById }
                    val unknownError = context.getString(R.string.unknown_error)
                    val validMangas =
                        validFailures
                            .mapNotNull { failure -> libraryById[failure.mangaId] }
                            .associateBy { it.id!! }
                    val uiItems =
                        validFailures.mapNotNull { failure ->
                            val manga = validMangas[failure.mangaId] ?: return@mapNotNull null
                            val source = sourceManager.getOrStub(manga.source)
                            FailedUpdateItem(
                                mangaId = failure.mangaId,
                                title = manga.title,
                                sourceId = manga.source,
                                sourceName = source.name,
                                error = failure.error?.takeIf { it.isNotBlank() } ?: unknownError,
                            )
                        }
                    validMangas to uiItems
                }

            if (!isBindingInitialized) return@launch
            loadedMangas = result.first
            adapter?.setItems(result.second)
            binding.progress.isVisible = false
            updateEmptyState()
        }
    }

    private fun onSelectionChanged(selectedIds: Set<Long>) {
        if (!isBindingInitialized) return
        val itemCount = adapter?.selectableItemCount() ?: 0
        updatingSelectAll = true
        binding.selectAll.isChecked = itemCount > 0 && selectedIds.size == itemCount
        updatingSelectAll = false
        binding.retrySelected.isInvisible = selectedIds.isEmpty() || retryStartedFromThisScreen
        updateRecyclerBottomPadding()
    }

    private fun updateRecyclerBottomPadding() {
        if (!isBindingInitialized) return
        binding.recycler.updatePadding(
            bottom = bottomInset + if (binding.retrySelected.isVisible) 88.dpToPx else 0,
        )
    }

    private fun updateEmptyState() {
        val hasItems = adapter?.hasItems() == true
        binding.selectAll.isVisible = hasItems
        if (hasItems) {
            binding.emptyView.hide()
        } else {
            binding.emptyView.show(R.drawable.ic_history_off_24dp, R.string.no_failed_updates)
            binding.retrySelected.isInvisible = true
        }
    }

    private fun openSourceWebView(sourceId: Long) {
        val activity = activity ?: return
        val source = sourceManager.get(sourceId) as? HttpSource ?: return
        val url = runCatching { source.getHomeUrl() }.getOrNull() ?: return
        startActivity(
            WebViewActivity.newIntent(
                activity,
                url,
                source.id,
                source.name,
            ),
        )
    }

    private fun retrySelected() {
        val selectedIds = adapter?.selectedMangaIds().orEmpty()
        if (selectedIds.isEmpty()) return
        val mangas = selectedIds.mapNotNull(loadedMangas::get)
        if (mangas.isEmpty()) {
            loadFailures()
            return
        }

        val started =
            LibraryUpdateJob.startNow(
                view?.context ?: return,
                mangaToUse = mangas,
                retryFailedUpdates = true,
            )
        if (started) {
            retryStartedFromThisScreen = true
            binding.retrySelected.isInvisible = true
        } else {
            activity?.toast(R.string.library_update_already_running)
        }
    }

    private fun showClearConfirmation() {
        val selectedIds = adapter?.selectedMangaIds().orEmpty()
        if (selectedIds.isEmpty()) return
        val activity = activity ?: return
        activity
            .materialAlertDialog()
            .setTitle(R.string.clear_failed_updates)
            .setMessage(R.string.clear_failed_updates_confirmation)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.clear) { _, _ ->
                viewScope.launch {
                    val remaining = withContext(Dispatchers.IO) { failureStore.remove(selectedIds) }
                    if (remaining.isEmpty()) {
                        activity?.notificationManager?.cancel(Notifications.ID_LIBRARY_ERROR)
                    }
                    loadFailures()
                }
            }.show()
    }
}
