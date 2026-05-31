package moe.shizuku.manager.apppermissions

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.AppListItemBinding
import moe.shizuku.manager.databinding.AppsActivityBinding
import moe.shizuku.manager.utils.AppIconCache
import moe.shizuku.manager.utils.ShizukuSystemApis
import moe.shizuku.manager.utils.UserHandleCompat
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.shizuku.Shizuku
import java.util.Locale

/**
 * Shows all user-launchable apps and lets you navigate into each one to
 * manage its fine-grained AppOps permissions via Shizuku.
 */
class AppPermissionsActivity : AppBarActivity() {

    private lateinit var binding: AppsActivityBinding
    private val adapter = AppListAdapter()

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        if (!isFinishing) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, R.string.app_permissions_shizuku_not_running, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = AppsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.app_permissions_title)

        val recyclerView = binding.list
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()
        recyclerView.addEdgeSpacing(top = 8f, bottom = 8f, unit = TypedValue.COMPLEX_UNIT_DIP)

        Shizuku.addBinderDeadListener(binderDeadListener)
        loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val pm = packageManager
            val userId = UserHandleCompat.myUserId()

            val apps = withContext(Dispatchers.IO) {
                try {
                    ShizukuSystemApis.getInstalledPackages(PackageManager.GET_META_DATA.toLong(), userId)
                        .filter { pi ->
                            // Only show apps with a launcher icon (user-visible apps)
                            pm.getLaunchIntentForPackage(pi.packageName) != null
                        }
                        .map { pi ->
                            val ai = pi.applicationInfo!!
                            AppListItem(pi, ai.loadLabel(pm).toString())
                        }
                        .sortedBy { it.label.lowercase(Locale.getDefault()) }
                } catch (e: Exception) {
                    emptyList()
                }
            }
            adapter.updateData(apps)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    data class AppListItem(val packageInfo: PackageInfo, val label: String)

    inner class AppListAdapter : RecyclerView.Adapter<AppListViewHolder>() {
        private val items = ArrayList<AppListItem>()

        fun updateData(newItems: List<AppListItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            AppListViewHolder(AppListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: AppListViewHolder, position: Int) =
            holder.bind(items[position])

        override fun getItemCount() = items.size
    }

    inner class AppListViewHolder(private val binding: AppListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppListItem) {
            val pm = itemView.context.packageManager
            val ai = item.packageInfo.applicationInfo!!

            binding.title.text = item.label
            binding.summary.text = item.packageInfo.packageName
            binding.switchWidget.visibility = android.view.View.GONE
            binding.requiresRoot.visibility = android.view.View.GONE

            AppIconCache.loadIconBitmapAsync(itemView.context, ai, ai.uid / 100000, binding.icon)

            itemView.setOnClickListener {
                val intent = Intent(itemView.context, AppPermissionDetailActivity::class.java).apply {
                    putExtra(AppPermissionDetailActivity.EXTRA_PACKAGE_NAME, item.packageInfo.packageName)
                    putExtra(AppPermissionDetailActivity.EXTRA_APP_LABEL, item.label)
                }
                itemView.context.startActivity(intent)
            }
        }
    }
}
