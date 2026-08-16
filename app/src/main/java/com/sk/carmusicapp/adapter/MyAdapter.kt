package com.sk.carmusicapp.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.recyclerview.widget.RecyclerView
import com.sk.carmusicapp.databinding.LayoutItemMusicBinding
import com.sk.carmusicapp.model.MyMusic
import com.squareup.picasso.Picasso


class MyAdapter(
    private val context: Context,
    private val musicList: List<MyMusic.Data>?,
    private val itemClickListener: ItemClickListener,
    private val currentPosition: Int

) : RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

    private var selectedItemPosition: Int = currentPosition
    private var lastPosition = -1

    inner class MyViewHolder(val binding: LayoutItemMusicBinding) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {


        init {
            binding.root.setOnClickListener(this)

        }

        fun bind(data: MyMusic.Data) {
            binding.songTitle.text = data.title
            binding.songTitle2.text = data.artist.name
            Picasso.get().load(data.album.cover).into(binding.imageViewControl)
            setAnimation(viewToAnimate = itemView, position)
            Log.d("TAG","currentPosition $currentPosition")
//            Log.d("TAG", "bind: $nextSongPosition")
//            Log.d("TAG", "bind: $previousSongPosition")
            if (selectedItemPosition == adapterPosition) {
                binding.parentCard.setBackgroundColor(Color.parseColor("#66EFEDED"))
            } else {
                binding.parentCard.setBackgroundColor(Color.parseColor("#66535050"))
            }

        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onClick(view: View?) {

            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val item = musicList?.get(position)
                item?.let {
                    // Update the UI with the selected song's information
                    itemClickListener.onItemClick(it, position)
                    // Update the selected item position
                    selectedItemPosition = position
                    notifyDataSetChanged()
                }
            }
        }


    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val binding = LayoutItemMusicBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        Log.d("TAG", "onCreateViewHolder: $currentPosition")

        return MyViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return musicList?.size ?: 0
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        Log.d("TAG","selectedpositiom $currentPosition")

        musicList?.get(position)?.let { holder.bind(it) }
    }

    interface ItemClickListener {
        fun onItemClick(item: MyMusic.Data, position: Int)
    }


    private fun setAnimation(viewToAnimate: View, position: Int) {
        // If the bound view wasn't previously displayed on screen, it's animated
        if (position > lastPosition) {
            val anim = ScaleAnimation(
                0.0f,
                1.0f,
                0.0f,
                1.0f,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
            anim.duration = 550 // Set animation duration
            viewToAnimate.startAnimation(anim)
            lastPosition = position
        }


    }


}

