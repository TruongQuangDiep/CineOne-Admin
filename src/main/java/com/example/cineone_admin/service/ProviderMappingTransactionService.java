package com.example.cineone_admin.service;

import com.example.cineone_admin.dto.sync.EntityCreationPair;
import com.example.cineone_admin.entity.*;
import com.example.cineone_admin.enums.*;
import com.example.cineone_admin.exception.ProviderMappingException;
import com.example.cineone_admin.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@Transactional(readOnly = true)
public class ProviderMappingTransactionService {
    /** Bên điều phối chia lô và gọi qua Spring bean để mỗi lô có một giao dịch riêng. */
    public static final int MAX_BATCH_SIZE = ProviderMappingService.MAX_BATCH_SIZE;
    private final ProviderMappingRepository mappings;
    private final DataProviderRepository providers;
    private final EntityManager entityManager;
    private final RoomTypeCatalogService roomTypes;

    public ProviderMappingTransactionService(ProviderMappingRepository mappings, DataProviderRepository providers,
            EntityManager entityManager, RoomTypeCatalogService roomTypes) {
        this.mappings = mappings;
        this.providers = providers;
        this.entityManager = entityManager;
        this.roomTypes = roomTypes;
    }

    public Optional<Long> findInternalId(Long providerId, ProviderEntityType type, String externalId) {
        return Optional.ofNullable(findInternalIds(providerId, type, List.of(validExternal(externalId))).get(externalId));
    }

    public Optional<String> findExternalId(Long providerId, ProviderEntityType type, Long internalId) {
        positive(internalId, "ID nội bộ");
        return Optional.ofNullable(findExternalIds(providerId, type, List.of(internalId)).get(internalId));
    }

    /** Tra cứu theo lô; chỉ trả những ID có ánh xạ hợp lệ, không tải toàn bộ thực thể nội bộ. */
    public Map<String, Long> findInternalIds(Long providerId, ProviderEntityType type, Collection<String> externalIds) {
        validateScope(providerId, type);
        var ids = externalIds(externalIds);
        Map<String, Long> result = new LinkedHashMap<>();
        for (var part : partitions(ids)) {
            var found = mappings.findByProviderProviderIdAndEntityTypeAndExternalIdIn(providerId, type.name(), part);
            requireExactExternalIds(found, part);
            requireUsable(found, type);
            found.forEach(m -> result.put(m.getExternalId(), m.getInternalId()));
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<Long, String> findExternalIds(Long providerId, ProviderEntityType type, Collection<Long> internalIds) {
        validateScope(providerId, type);
        Objects.requireNonNull(internalIds, "Danh sách ID nội bộ là bắt buộc");
        var ids = new ArrayList<>(new LinkedHashSet<>(internalIds));
        ids.forEach(id -> positive(id, "ID nội bộ"));
        Map<Long, String> result = new LinkedHashMap<>();
        for (var part : partitions(ids)) {
            var found = mappings.findByProviderProviderIdAndEntityTypeAndInternalIdIn(providerId, type.name(), part);
            requireUsable(found, type);
            found.forEach(m -> result.put(m.getInternalId(), m.getExternalId()));
        }
        return Collections.unmodifiableMap(result);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long resolveOrCreate(Long providerId, ProviderEntityType type, String externalId, Supplier<?> newEntity) {
        return resolveOrCreateBatch(providerId, type, List.of(new EntityCreationPair<>(externalId, newEntity))).get(externalId);
    }

    /** Mỗi lô nguyên tử: lỗi bất kỳ bản ghi nào sẽ hoàn tác toàn lô. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Long> resolveOrCreateBatch(Long providerId, ProviderEntityType type,
            List<? extends EntityCreationPair<?>> items) {
        validateScope(providerId, type);
        Objects.requireNonNull(items, "Danh sách thực thể mới là bắt buộc");
        checkBatchSize(items.size());
        Map<String, Supplier<?>> factories = new LinkedHashMap<>();
        for (var item : items) {
            Objects.requireNonNull(item, "Phần tử trong lô không được null");
            if (factories.putIfAbsent(item.externalId(), item.newEntity()) != null)
                throw new IllegalArgumentException("ID bên ngoài bị lặp trong lô: " + item.externalId());
        }
        if (items.isEmpty()) return Map.of();
        DataProvider provider = requireProvider(providerId);
        var found = mappings.findForUpdateByProviderProviderIdAndEntityTypeAndExternalIdIn(
                providerId, type.name(), factories.keySet());
        requireExactExternalIds(found, factories.keySet());
        requireUsable(found, type);
        Map<String, Long> result = new LinkedHashMap<>();
        found.forEach(m -> result.put(m.getExternalId(), m.getInternalId()));
        Map<String, Object> pending = new LinkedHashMap<>();
        var identifier = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        for (var entry : factories.entrySet()) {
            if (result.containsKey(entry.getKey())) continue;
            Object entity = entry.getValue().get();
            if (entity == null || !type.getEntityClass().isInstance(entity))
                throw new IllegalArgumentException("Thực thể mới phải thuộc lớp " + type.getEntityClass().getSimpleName());
            if (identifier.getIdentifier(entity) != null)
                throw new IllegalArgumentException("Thực thể mới chưa được có ID nội bộ");
            pending.put(entry.getKey(), entity);
        }
        if (type == ProviderEntityType.ROOM)
            roomTypes.registerTypes(pending.values().stream().map(Room.class::cast).map(Room::getRoomType).toList());
        for (var entry : pending.entrySet()) {
            entityManager.persist(entry.getValue());
            // Với IDENTITY, Hibernate thực hiện INSERT để lấy ID ngay khi persist.
            Long id = (Long) identifier.getIdentifier(entry.getValue());
            if (id == null) throw new ProviderMappingException("Cơ sở dữ liệu chưa sinh ID nội bộ");
            entityManager.persist(newMapping(provider, type, entry.getKey(), id));
            result.put(entry.getKey(), id);
        }
        if (!pending.isEmpty()) entityManager.flush();
        return Collections.unmodifiableMap(result);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProviderMapping bind(Long providerId, ProviderEntityType type, String externalId, Long internalId) {
        validExternal(externalId);
        positive(internalId, "ID nội bộ");
        return bindBatch(providerId, type, Map.of(externalId, internalId)).get(externalId);
    }

    /** Đăng ký hoặc ghi nhận đồng bộ thành công; không tự khôi phục ánh xạ đã bị vô hiệu hóa. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, ProviderMapping> bindBatch(Long providerId, ProviderEntityType type, Map<String, Long> pairs) {
        validateScope(providerId, type);
        Objects.requireNonNull(pairs, "Danh sách cặp ID là bắt buộc");
        checkBatchSize(pairs.size());
        pairs.forEach((external, internal) -> { validExternal(external); positive(internal, "ID nội bộ"); });
        if (new HashSet<>(pairs.values()).size() != pairs.size())
            throw new ProviderMappingException("Một ID nội bộ không được xuất hiện trong nhiều cặp ánh xạ");
        if (pairs.isEmpty()) return Map.of();
        DataProvider provider = requireProvider(providerId);
        var byExternal = mappings.findForUpdateByProviderProviderIdAndEntityTypeAndExternalIdIn(
                providerId, type.name(), pairs.keySet());
        var byInternal = mappings.findForUpdateByProviderProviderIdAndEntityTypeAndInternalIdIn(
                providerId, type.name(), pairs.values());
        for (var m : byExternal) {
            if (!m.getInternalId().equals(pairs.get(m.getExternalId())))
                throw new ProviderMappingException("ID bên ngoài đã được ánh xạ tới một ID nội bộ khác");
        }
        for (var m : byInternal) {
            if (!m.getInternalId().equals(pairs.get(m.getExternalId())))
                throw new ProviderMappingException("ID nội bộ đã được ánh xạ tới một ID bên ngoài khác");
        }
        requireActive(byExternal);
        if (type == ProviderEntityType.ROOM) {
            // Lấy các phòng theo lô để đọc cả loại mới đang được sửa trong persistence context.
            var rooms = entityManager.createQuery("select r from Room r where r.roomId in :ids", Room.class)
                    .setFlushMode(FlushModeType.COMMIT).setParameter("ids", pairs.values()).getResultList();
            roomTypes.registerTypes(rooms.stream().map(Room::getRoomType).toList());
        }
        requireTargets(type, pairs.values());
        Map<String, ProviderMapping> result = new LinkedHashMap<>();
        byExternal.forEach(m -> result.put(m.getExternalId(), m));
        LocalDateTime now = LocalDateTime.now();
        for (var pair : pairs.entrySet()) {
            ProviderMapping mapping = result.get(pair.getKey());
            if (mapping == null) {
                mapping = newMapping(provider, type, pair.getKey(), pair.getValue());
                entityManager.persist(mapping);
                result.put(pair.getKey(), mapping);
            }
            mapping.setLastSyncedAt(now);
        }
        entityManager.flush();
        return Collections.unmodifiableMap(result);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void changeStatus(Long providerId, ProviderEntityType type, String externalId, ProviderMappingStatus status) {
        changeStatusBatch(providerId, type, List.of(validExternal(externalId)), status);
    }

    /** Chuyển trạng thái tường minh, không sửa thời điểm đồng bộ dữ liệu. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void changeStatusBatch(Long providerId, ProviderEntityType type, Collection<String> externalIds,
            ProviderMappingStatus status) {
        validateScope(providerId, type);
        Objects.requireNonNull(status, "Trạng thái ánh xạ là bắt buộc");
        var ids = externalIds(externalIds);
        checkBatchSize(ids.size());
        if (ids.isEmpty()) return;
        requireProvider(providerId);
        var found = mappings.findForUpdateByProviderProviderIdAndEntityTypeAndExternalIdIn(providerId, type.name(), ids);
        requireExactExternalIds(found, ids);
        if (found.size() != ids.size()) throw new ProviderMappingException("Có ID chưa được ánh xạ trong yêu cầu đổi trạng thái");
        if (status == ProviderMappingStatus.ACTIVE)
            requireTargets(type, found.stream().map(ProviderMapping::getInternalId).toList());
        found.forEach(m -> m.setStatus(status.name()));
        entityManager.flush();
    }

    private ProviderMapping newMapping(DataProvider provider, ProviderEntityType type, String externalId, Long id) {
        var mapping = new ProviderMapping();
        mapping.setProvider(provider);
        mapping.setEntityType(type.name());
        mapping.setExternalId(externalId);
        mapping.setInternalId(id);
        mapping.setStatus(ProviderMappingStatus.ACTIVE.name());
        mapping.setLastSyncedAt(LocalDateTime.now());
        return mapping;
    }

    private DataProvider requireProvider(Long providerId) {
        if (!providers.existsById(providerId))
            throw new ProviderMappingException("Nhà cung cấp không tồn tại: " + providerId);
        return entityManager.getReference(DataProvider.class, providerId);
    }

    private void requireExactExternalIds(List<ProviderMapping> found, Collection<String> requested) {
        Set<String> exact = new HashSet<>(requested);
        for (var mapping : found) {
            if (!exact.contains(mapping.getExternalId()))
                throw new ProviderMappingException("ID bên ngoài không khớp chính xác; cần kiểm tra quy tắc so sánh chuỗi của cơ sở dữ liệu");
        }
    }

    private void requireUsable(List<ProviderMapping> found, ProviderEntityType type) {
        requireActive(found);
        requireTargets(type, found.stream().map(ProviderMapping::getInternalId).toList());
    }

    private void requireActive(List<ProviderMapping> found) {
        for (var m : found) {
            if (!ProviderMappingStatus.ACTIVE.name().equals(m.getStatus()))
                throw new ProviderMappingException("Ánh xạ không ở trạng thái ACTIVE: " + m.getExternalId());
        }
    }

    private void requireTargets(ProviderEntityType type, Collection<Long> ids) {
        if (ids.isEmpty()) return;
        // Chỉ lấy cột ID; tránh tải thực thể và các quan hệ EAGER vào bộ nhớ.
        var meta = entityManager.getMetamodel().entity(type.getEntityClass());
        String id = meta.getId(Long.class).getName();
        var existing = entityManager.createQuery("select e." + id + " from " + meta.getName()
                + " e where e." + id + " in :ids", Long.class).setParameter("ids", ids).getResultList();
        if (!new HashSet<>(existing).containsAll(ids))
            throw new ProviderMappingException("Có ánh xạ trỏ tới thực thể nội bộ không còn tồn tại");
    }

    private static void validateScope(Long providerId, ProviderEntityType type) {
        positive(providerId, "ID nhà cung cấp");
        Objects.requireNonNull(type, "Loại thực thể là bắt buộc");
    }

    private static String validExternal(String externalId) {
        if (externalId == null || externalId.isBlank() || externalId.length() > 255)
            throw new IllegalArgumentException("ID bên ngoài là bắt buộc và tối đa 255 ký tự");
        return externalId;
    }

    private static ArrayList<String> externalIds(Collection<String> ids) {
        Objects.requireNonNull(ids, "Danh sách ID bên ngoài là bắt buộc");
        ids.forEach(ProviderMappingTransactionService::validExternal);
        return new ArrayList<>(new LinkedHashSet<>(ids));
    }

    private static void checkBatchSize(int size) {
        if (size > MAX_BATCH_SIZE)
            throw new IllegalArgumentException("Mỗi lô ghi tối đa " + MAX_BATCH_SIZE + " bản ghi");
    }

    private static <T> List<List<T>> partitions(List<T> items) {
        List<List<T>> result = new ArrayList<>();
        for (int start = 0; start < items.size(); start += MAX_BATCH_SIZE)
            result.add(items.subList(start, Math.min(start + MAX_BATCH_SIZE, items.size())));
        return result;
    }

    private static void positive(Long id, String name) {
        if (id == null || id <= 0) throw new IllegalArgumentException(name + " phải là số nguyên dương");
    }
}
