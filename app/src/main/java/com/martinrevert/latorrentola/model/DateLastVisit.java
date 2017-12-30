package com.martinrevert.latorrentola.model;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

import java.util.Date;


/**
 * Created by martin on 21/12/17.
 */
@Entity(tableName = "date")
public class DateLastVisit{
    @PrimaryKey(autoGenerate = true)
    private int id;
    @ColumnInfo
    private Date date;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }


}
