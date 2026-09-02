package com.example.on_safe.ui.login

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.on_safe.R
import com.example.on_safe.network.dto.JusoItem

class AddressAdapter(
    private val items: List<JusoItem>,
    private val onClick: (JusoItem) -> Unit
) : RecyclerView.Adapter<AddressAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvRoadAddr: TextView = view.findViewById(R.id.tvRoadAddr)
        val tvJibunAddr: TextView = view.findViewById(R.id.tvJibunAddr)
        val tvZipNo: TextView = view.findViewById(R.id.tvZipNo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_address, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvRoadAddr.text = item.roadAddr
        holder.tvJibunAddr.text = item.jibunAddr
        holder.tvZipNo.text = "[${item.zipNo}]"
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
