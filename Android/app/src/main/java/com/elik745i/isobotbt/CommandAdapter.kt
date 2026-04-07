package com.elik745i.isobotbt

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.elik745i.isobotbt.databinding.ItemCommandCardBinding

class CommandAdapter(
    private val onSendClicked: (RobotCommand) -> Unit,
) : RecyclerView.Adapter<CommandAdapter.CommandViewHolder>() {

    private val items = mutableListOf<RobotCommand>()

    fun submitList(commands: List<RobotCommand>) {
        items.clear()
        items.addAll(commands)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommandViewHolder {
        val binding = ItemCommandCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommandViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommandViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CommandViewHolder(
        private val binding: ItemCommandCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(command: RobotCommand) {
            binding.commandIndex.text = command.index.toString()
            binding.commandName.text = command.label
            binding.commandDetails.text = binding.root.context.getString(
                R.string.command_details,
                command.category.title,
                command.codeName,
            )
            binding.sendCommandButton.setOnClickListener { onSendClicked(command) }
        }
    }
}