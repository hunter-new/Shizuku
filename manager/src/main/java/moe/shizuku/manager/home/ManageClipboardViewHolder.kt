package moe.shizuku.manager.home

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import moe.shizuku.manager.R
import moe.shizuku.manager.databinding.HomeItemContainerBinding
import moe.shizuku.manager.databinding.HomeManageAppsItemBinding
import moe.shizuku.manager.management.ClipboardManagementActivity
import moe.shizuku.manager.model.ServiceStatus
import rikka.recyclerview.BaseViewHolder
import rikka.recyclerview.BaseViewHolder.Creator

class ManageClipboardViewHolder(private val binding: HomeManageAppsItemBinding, root: View) :
    BaseViewHolder<ServiceStatus>(root), View.OnClickListener {

    companion object {
        val CREATOR = Creator<ServiceStatus> { inflater: LayoutInflater, parent: ViewGroup? ->
            val outer = HomeItemContainerBinding.inflate(inflater, parent, false)
            val inner = HomeManageAppsItemBinding.inflate(inflater, outer.root, true)
            ManageClipboardViewHolder(inner, outer.root)
        }
    }

    init {
        root.setOnClickListener(this)
    }

    private inline val title get() = binding.text1
    private inline val summary get() = binding.text2

    override fun onBind() {
        val context = itemView.context
        val running = data.isRunning
        itemView.isEnabled = running
        if (!running) {
            title.setText(R.string.home_clipboard_management_title)
            summary.text = context.getString(
                R.string.home_status_service_not_running,
                context.getString(R.string.app_name)
            )
        } else {
            title.setText(R.string.home_clipboard_management_title)
            summary.setText(R.string.home_clipboard_management_summary)
        }
    }

    override fun onClick(v: View) {
        v.context.startActivity(Intent(v.context, ClipboardManagementActivity::class.java))
    }
}
