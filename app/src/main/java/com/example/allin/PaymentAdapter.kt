package com.example.allin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.allin.data.Payment
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class PaymentAdapter(
    private val onEdit: (Payment) -> Unit,
    private val onDelete: (Payment) -> Unit
) : ListAdapter<Payment, PaymentAdapter.PaymentViewHolder>(PaymentDiffCallback()) {

    private val moneyFormat = DecimalFormat("#,###원")
    private val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_payment, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val payment = getItem(position)
        holder.bind(payment)
    }

    inner class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStoreName: TextView = itemView.findViewById(R.id.tvStoreName)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        // [수정] Button -> TextView로 변경 (XML과 일치시킴)
        private val btnEdit: TextView = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: TextView = itemView.findViewById(R.id.btnDelete)

        fun bind(payment: Payment) {
            tvStoreName.text = payment.storeName
            tvAmount.text = moneyFormat.format(payment.amount)
            tvDate.text = dateFormat.format(Date(payment.date))
            tvCategory.text = payment.category

            itemView.setOnClickListener { onEdit(payment) }
            btnEdit.setOnClickListener { onEdit(payment) }
            btnDelete.setOnClickListener { onDelete(payment) }
        }
    }

    class PaymentDiffCallback : DiffUtil.ItemCallback<Payment>() {
        override fun areItemsTheSame(oldItem: Payment, newItem: Payment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Payment, newItem: Payment): Boolean {
            return oldItem == newItem
        }
    }
}
