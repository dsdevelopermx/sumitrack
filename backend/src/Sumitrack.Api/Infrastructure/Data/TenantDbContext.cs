using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Models.Entities;

namespace Sumitrack.Api.Infrastructure.Data;

public class TenantDbContext : DbContext
{
    public TenantDbContext(DbContextOptions<TenantDbContext> options) : base(options) { }

    public DbSet<User> Users => Set<User>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<User>(entity =>
        {
            entity.ToTable("users");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id)
                .HasColumnName("id")
                .HasColumnType("uuid");
            entity.Property(e => e.Username)
                .HasColumnName("username")
                .HasColumnType("character varying(100)")
                .HasMaxLength(100)
                .IsRequired();
            entity.HasIndex(e => e.Username)
                .HasDatabaseName("ix_users_username")
                .IsUnique();
            entity.Property(e => e.PasswordHash)
                .HasColumnName("password_hash")
                .HasColumnType("character varying(255)")
                .HasMaxLength(255)
                .IsRequired();
            entity.Property(e => e.TenantId)
                .HasColumnName("tenant_id")
                .HasColumnType("uuid")
                .IsRequired();
            entity.Property(e => e.CreatedAt)
                .HasColumnName("created_at")
                .HasColumnType("timestamp with time zone")
                .HasDefaultValueSql("NOW()");
            entity.Property(e => e.UpdatedAt)
                .HasColumnName("updated_at")
                .HasColumnType("timestamp with time zone")
                .HasDefaultValueSql("NOW()");
        });
    }
}
