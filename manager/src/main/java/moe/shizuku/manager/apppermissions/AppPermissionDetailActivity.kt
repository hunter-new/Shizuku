package moe.shizuku.manager.apppermissions

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.AppsActivityBinding
import rikka.recyclerview.addEdgeSpacing
import rikka.recyclerview.fixEdgeEffect
import rikka.shizuku.Shizuku

/**
 * Shows all managed AppOps permissions for a single app, and lets the user
 * toggle each one on/off (or reset to default) via Shizuku.
 */
class AppPermissionDetailActivity : AppBarActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_LABEL = "app_label"
    }

    private lateinit var binding: AppsActivityBinding
    private lateinit var packageName: String
    private val adapter = PermissionAdapter()

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        if (!isFinishing) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run { finish(); return }
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL) ?: packageName

        if (!Shizuku.pingBinder()) {
            finish()
            return
        }

        binding = AppsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = appLabel

        val recyclerView = binding.list
        recyclerView.adapter = adapter
        recyclerView.fixEdgeEffect()
        recyclerView.addEdgeSpacing(top = 8f, bottom = 8f, unit = TypedValue.COMPLEX_UNIT_DIP)

        Shizuku.addBinderDeadListener(binderDeadListener)
        loadPermissions()
    }

    private fun loadPermissions() {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                AppPermissionsManager.MANAGED_OPS.map { op ->
                    val mode = AppPermissionsManager.getMode(packageName, op.opName)
                    PermissionItem(op, mode)
                }
            }
            adapter.updateData(items)
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

    // ─── Data ───────────────────────────────────────────────────────────────

    data class PermissionItem(
        val op: AppPermissionsManager.AppOp,
        var mode: AppPermissionsManager.OpMode,
    )

    // ─── Adapter ─────────────────────────────────────────────────────────────

    inner class PermissionAdapter : RecyclerView.Adapter<PermissionViewHolder>() {
        private val items = ArrayList<PermissionItem>()

        fun updateData(newItems: List<PermissionItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PermissionViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.permission_item, parent, false)
            return PermissionViewHolder(view)
        }

        override fun onBindViewHolder(holder: PermissionViewHolder, position: Int) =
            holder.bind(items[position])

        override fun getItemCount() = items.size
    }

    // ─── ViewHolder ──────────────────────────────────────────────────────────

    inner class PermissionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val labelView: TextView = view.findViewById(R.id.permission_label)
        private val descriptionView: TextView = view.findViewById(R.id.permission_description)
        private val modeView: TextView = view.findViewById(R.id.permission_mode)
        private val switchWidget: MaterialSwitch = view.findViewById(R.id.permission_switch)

        fun bind(item: PermissionItem) {
            labelView.text = item.op.label
            descriptionView.text = item.op.description
            updateModeUI(item)

            // Remove old listener to prevent spurious triggers when recycling
            switchWidget.setOnCheckedChangeListener(null)
            switchWidget.isChecked = item.mode == AppPermissionsManager.OpMode.ALLOW ||
                    item.mode == AppPermissionsManager.OpMode.FOREGROUND

            switchWidget.setOnCheckedChangeListener { _, isChecked ->
                val newMode = if (isChecked) AppPermissionsManager.OpMode.ALLOW else AppPermissionsManager.OpMode.IGNORE
                item.mode = newMode
                updateModeUI(item)

                lifecycleScope.launch {
                    val success = withContext(Dispatchers.IO) {
                        try {
                            AppPermissionsManager.setMode(packageName, item.op.opName, newMode)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }
                    if (!success) {
                        // Revert UI on failure
                        val prevMode = if (isChecked) AppPermissionsManager.OpMode.IGNORE else AppPermissionsManager.OpMode.ALLOW
                        item.mode = prevMode
                        switchWidget.setOnCheckedChangeListener(null)
                        switchWidget.isChecked = !isChecked
                        updateModeUI(item)
                        Snackbar.make(binding.root, R.string.app_permissions_set_failed, Snackbar.LENGTH_SHORT).show()
                    } else {
                        val msg = if (isChecked)
                            getString(R.string.app_permissions_allowed, item.op.label)
                        else
                            getString(R.string.app_permissions_blocked, item.op.label)
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                    }
                }
            }

            itemView.setOnClickListener { switchWidget.toggle() }
        }

        private fun updateModeUI(item: PermissionItem) {
            val (modeText, modeColor) = when (item.mode) {
                AppPermissionsManager.OpMode.ALLOW -> Pair(
                    item.mode.displayName,
                    itemView.context.getColor(android.R.color.holo_green_dark)
                )
                AppPermissionsManager.OpMode.FOREGROUND -> Pair(
                    item.mode.displayName,
                    itemView.context.getColor(android.R.color.holo_blue_dark)
                )
                AppPermissionsManager.OpMode.IGNORE,
                AppPermissionsManager.OpMode.DENY -> Pair(
                    item.mode.displayName,
                    itemView.context.getColor(android.R.color.holo_red_dark)
                )
                AppPermissionsManager.OpMode.DEFAULT -> Pair(
                    item.mode.displayName,
                    itemView.context.getColor(android.R.color.darker_gray)
                )
            }
            modeView.text = modeText
            modeView.setTextColor(modeColor)
        }
    }
}
