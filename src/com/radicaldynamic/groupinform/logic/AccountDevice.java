package com.radicaldynamic.groupinform.logic;

import java.io.Serializable;

public class AccountDevice implements Serializable
{
    /**
     * 
     */
    private static final long serialVersionUID = 8198183377512045250L;

    private String id;                  // RO access by everyone
    private String alias;               // Everyone (r/w by device owner)
    private String email;               // Everyone (r/w by device owner)
    private String lastCheckin;         // ?
    private String pin;                 // RO by account owner, device owner
    private String status;              // R/W by account owner
    private String transferStatus;      // R/W by account owner, device owner
    
    
    public AccountDevice(String id, String alias, String email)
    {
        setId(id);
        setAlias(alias);
        setEmail(email);
    }
    
    public void setId(String id) { this.id = id; }
    public String getId() { return id; }
    
    public void setAlias(String alias) { this.alias = alias; }
    public String getAlias() { if (alias == null || alias.equals("null")) return null; else return alias; }
    
    public void setEmail(String email) { this.email = email; }
    public String getEmail() { return email; }
    
    // Retrieve a reasonable display name (prefer the alias but fall back to email address)
    public String getDisplayName()
    {
        if (getAlias() == null || getAlias().equals("null"))
            return getEmail();
        else
            return getAlias();           
    }
    
    /*
     * Setters and getters listed below are used by account and/or device owners only
     */
    
    public void setLastCheckin(String lastCheckin) { this.lastCheckin = lastCheckin; }
    public String getLastCheckin() { return lastCheckin; }
    
    public void setPin(String pin) { this.pin = pin; }
    public String getPin() { return pin; }

    public void setStatus(String status) { this.status = status; }
    public String getStatus() { return status; }
    
    public void setTransferStatus(String transferStatus) { this.transferStatus = transferStatus; }
    public String getTransferStatus() { return transferStatus; }
}