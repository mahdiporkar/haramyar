package ir.haramyar.martyrs.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProfileJpaRepository extends JpaRepository<Entities.ProfileEntity, UUID> {
  Optional<Entities.ProfileEntity> findBySlug(String slug);

  boolean existsBySlugAndIdNot(String slug, UUID id);

  boolean existsBySlug(String slug);

  @Query("""
      select p from ProfileEntity p
      where p.status = 'PUBLISHED' and (:query = '' or p.searchText like concat('%', :query, '%'))
      order by p.publishedAt desc""")
  Page<Entities.ProfileEntity> searchPublished(@Param("query") String query, Pageable page);

  @Query("""
      select p from ProfileEntity p
      where p.status in :statuses and (:query = '' or p.searchText like concat('%', :query, '%'))
      order by p.updatedAt desc""")
  Page<Entities.ProfileEntity> editorialIndex(@Param("statuses") List<String> statuses, @Param("query") String query, Pageable page);

  @Query("select p from ProfileEntity p where p.status = 'PUBLISHED' order by p.slug")
  List<Entities.ProfileEntity> published();
}

interface HistoryJpaRepository extends JpaRepository<Entities.HistoryEntity, Long> {
  List<Entities.HistoryEntity> findByProfileIdOrderBySequenceNo(UUID profileId);
}

interface MediaJpaRepository extends JpaRepository<Entities.MediaEntity, UUID> {}
