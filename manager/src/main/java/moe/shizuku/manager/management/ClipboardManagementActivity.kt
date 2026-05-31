package moe.shizuku.manager.management

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.util.*

class ClipboardManagementActivity : AppBarActivity() {

    private lateinit var binding: AppsActivityBinding
    private val adapter = ClipboardAppsAdapter()

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        if (!isFinishing) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Shizuku.pingBinder()) {
            finish()
            return
        }

        binding = AppsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.home_clipboard_management_title)

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
            val currentUserId = UserHandleCompat.myUserId()
            
            val packageList = withContext(Dispatchers.IO) {
                try {
                    ShizukuSystemApis.getInstalledPackages(PackageManager.GET_META_DATA.toLong(), currentUserId)
                } catch (e: Exception) {
                    emptyList()
                }
            }

            val appItems = withContext(Dispatchers.IO) {
                packageList.filter { pi ->
                    // Show apps that have launcher intents
                    pm.getLaunchIntentForPackage(pi.packageName) != null
                }.map { pi ->
                    async {
                        val ai = pi.applicationInfo!!
                        val label = ai.loadLabel(pm).toString()
                        val isAllowed = checkClipboardAllowed(pi.packageName)
                        ClipboardAppItem(pi, label, isAllowed)
                    }
                }.awaitAll().sortedBy { it.label.lowercase(Locale.getDefault()) }
            }

            adapter.updateData(appItems)
        }
    }

    private suspend fun checkClipboardAllowed(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = moe.shizuku.server.IShizukuService.Stub.asInterface(Shizuku.getBinder())
            val p = service.newProcess(arrayOf("cmd", "appops", "get", packageName, "READ_CLIPBOARD"), null, null)
            val sb = java.lang.StringBuilder()
            (p.inputStream as java.io.InputStream).bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
            }
            p.waitFor()
            sb.toString().contains("allow")
        } catch (e: Exception) {
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class ClipboardAppItem(
        val packageInfo: PackageInfo,
        val label: String,
        var isAllowed: Boolean
    )

    inner class ClipboardAppsAdapter : RecyclerView.Adapter<ClipboardAppViewHolder>() {
        private val items = ArrayList<ClipboardAppItem>()

        fun updateData(newItems: List<ClipboardAppItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipboardAppViewHolder {
            val binding = AppListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ClipboardAppViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ClipboardAppViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size
    }

    inner class ClipboardAppViewHolder(private val binding: AppListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ClipboardAppItem) {
            val pm = itemView.context.packageManager
            val ai = item.packageInfo.applicationInfo!!
            
            binding.icon.setImageDrawable(ai.loadIcon(pm))
            binding.title.text = item.label
            binding.summary.text = item.packageInfo.packageName
            
            // Remove listener before setting checked status to avoid trigger
            binding.switchWidget.setOnCheckedChangeListener(null)
            binding.switchWidget.isChecked = item.isAllowed
            binding.requiresRoot.visibility = View.GONE

            binding.switchWidget.setOnCheckedChangeListener { _, isChecked ->
                item.isAllowed = isChecked
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        setClipboardAllowed(item.packageInfo.packageName, isChecked)
                    }
                    val text = if (isChecked) {
                        itemView.context.getString(R.string.home_clipboard_management_allowed, item.label)
                    } else {
                        itemView.context.getString(R.string.home_clipboard_management_blocked, item.label)
                    }
                    Toast.makeText(itemView.context, text, Toast.LENGTH_SHORT).show()
                }
            }

            itemView.setOnClickListener {
                binding.switchWidget.toggle()
            }

            AppIconCache.loadIconBitmapAsync(itemView.context, ai, ai.uid / 100000, binding.icon)
        }

        private fun setClipboardAllowed(packageName: String, allowed: Boolean) {
            val op = if (allowed) "allow" else "ignore"
            try {
                val service = moe.shizuku.server.IShizukuService.Stub.asInterface(Shizuku.getBinder())
                val p = service.newProcess(arrayOf("cmd", "appops", "set", packageName, "READ_CLIPBOARD", op), null, null)
                p.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
