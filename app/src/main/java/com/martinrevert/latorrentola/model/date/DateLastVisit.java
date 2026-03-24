package com.martinrevert.latorrentola.model.date;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;


/**
 * Created by martin on 21/12/17.
 */
@Entity(tableName = "date")
public class DateLastVisit {
    @PrimaryKey
    private int id = 1;
    
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
