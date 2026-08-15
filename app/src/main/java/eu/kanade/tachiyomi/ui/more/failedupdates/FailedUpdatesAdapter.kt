package eu.kanade.tachiyomi.ui.more.failedupdates

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.FailedUpdateErrorHeaderBinding
import eu.kanade.tachiyomi.databinding.FailedUpdateItemBinding
import java.util.Locale

data class FailedUpdateItem(
    val mangaId: Long,
    val title: String,
    val sourceId: Long,
    val sourceName: String,
    val error: String,
)

class FailedUpdatesAdapter(
    private val onSelectionChanged: (Set<Long>) -> Unit,
    private val onOpenSourceWebView: (Long) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private data class GroupKey(
        val sourceId: Long,
        val error: String,
    )

    private sealed interface Row {
        data class ErrorHeader(
            val key: GroupKey,
            val sourceName: String,
            val error: String,
            val mangaIds: List<Long>,
            val collapsed: Boolean,
        ) : Row

        data class Manga(
            val item: FailedUpdateItem,
        ) : Row
    }

    private val selectedIds = linkedSetOf<Long>()
    private val collapsedGroups = linkedSetOf<GroupKey>()
    private var items = emptyList<FailedUpdateItem>()
    private var rows = emptyList<Row>()

    fun setItems(newItems: List<FailedUpdateItem>) {
        items = newItems
        selectedIds.retainAll(newItems.mapTo(mutableSetOf()) { it.mangaId })
        val validKeys = newItems.mapTo(mutableSetOf()) { GroupKey(it.sourceId, it.error) }
        collapsedGroups.retainAll(validKeys)
        rebuildRows()
        notifyDataSetChanged()
        dispatchSelectionChanged()
    }

    private fun rebuildRows() {
        rows =
            items
                .groupBy { GroupKey(it.sourceId, it.error) }
                .entries
                .sortedWith(
                    compareBy<Map.Entry<GroupKey, List<FailedUpdateItem>>> {
                        it.value.firstOrNull()?.sourceName?.lowercase(Locale.ROOT).orEmpty()
                    }.thenBy { it.key.error.lowercase(Locale.ROOT) },
                ).flatMap { (key, groupItems) ->
                    val sortedItems = groupItems.sortedBy { it.title.lowercase(Locale.ROOT) }
                    val collapsed = key in collapsedGroups
                    listOf<Row>(
                        Row.ErrorHeader(
                            key = key,
                            sourceName = sortedItems.first().sourceName,
                            error = key.error,
                            mangaIds = sortedItems.map { it.mangaId },
                            collapsed = collapsed,
                        ),
                    ) + if (collapsed) emptyList() else sortedItems.map { Row.Manga(it) }
                }
    }

    fun selectAll(selected: Boolean) {
        selectedIds.clear()
        if (selected) selectedIds.addAll(items.map { it.mangaId })
        notifyDataSetChanged()
        dispatchSelectionChanged()
    }

    fun selectedMangaIds(): Set<Long> = selectedIds.toSet()

    fun hasItems(): Boolean = items.isNotEmpty()

    fun selectableItemCount(): Int = items.size

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        when (rows[position]) {
            is Row.ErrorHeader -> VIEW_TYPE_ERROR
            is Row.Manga -> VIEW_TYPE_MANGA
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_ERROR -> ErrorHeaderHolder(FailedUpdateErrorHeaderBinding.inflate(inflater, parent, false))
            else -> MangaHolder(FailedUpdateItemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.ErrorHeader -> (holder as ErrorHeaderHolder).bind(row)
            is Row.Manga -> (holder as MangaHolder).bind(row.item)
        }
    }

    private fun toggleErrorGroup(mangaIds: List<Long>, selected: Boolean) {
        if (selected) selectedIds.addAll(mangaIds) else selectedIds.removeAll(mangaIds.toSet())
        notifyDataSetChanged()
        dispatchSelectionChanged()
    }

    private fun toggleCollapsed(key: GroupKey) {
        if (!collapsedGroups.add(key)) collapsedGroups.remove(key)
        rebuildRows()
        notifyDataSetChanged()
    }

    private fun toggleManga(mangaId: Long, selected: Boolean) {
        if (selected) selectedIds.add(mangaId) else selectedIds.remove(mangaId)
        notifyDataSetChanged()
        dispatchSelectionChanged()
    }

    private fun dispatchSelectionChanged() = onSelectionChanged(selectedIds.toSet())

    private inner class ErrorHeaderHolder(
        private val binding: FailedUpdateErrorHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: Row.ErrorHeader) {
            binding.sourceName.text = row.sourceName
            binding.error.text =
                binding.root.context.getString(
                    R.string.failed_update_error_group,
                    row.error,
                    row.mangaIds.size,
                )
            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = row.mangaIds.isNotEmpty() && row.mangaIds.all(selectedIds::contains)
            binding.checkbox.setOnCheckedChangeListener { _, checked -> toggleErrorGroup(row.mangaIds, checked) }
            binding.webviewButton.setOnClickListener { onOpenSourceWebView(row.key.sourceId) }

            binding.expandIcon.setImageResource(
                if (row.collapsed) R.drawable.ic_expand_more_24dp else R.drawable.ic_expand_less_24dp,
            )
            binding.expandArea.setOnClickListener { toggleCollapsed(row.key) }
            binding.sourceName.setOnClickListener { toggleCollapsed(row.key) }
            binding.error.setOnClickListener { toggleCollapsed(row.key) }
        }
    }

    private inner class MangaHolder(
        private val binding: FailedUpdateItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FailedUpdateItem) {
            binding.title.text = item.title
            binding.source.text = item.sourceName
            binding.checkbox.setOnCheckedChangeListener(null)
            binding.checkbox.isChecked = item.mangaId in selectedIds
            binding.checkbox.setOnCheckedChangeListener { _, checked -> toggleManga(item.mangaId, checked) }
            binding.root.setOnClickListener { binding.checkbox.isChecked = !binding.checkbox.isChecked }
        }
    }

    private companion object {
        private const val VIEW_TYPE_ERROR = 0
        private const val VIEW_TYPE_MANGA = 1
    }
}
