package com.martinrevert.latorrentola.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

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
